/* Bakir Khata single-device encryption demonstration.
   This is NOT Signal Protocol: there is no Double Ratchet, multi-device syncing,
   identity-key verification ceremony, forward secrecy, or secure key recovery.
   Uses browser WebCrypto ECDH P-256 -> AES-GCM. The backend stores ciphertext only. */
(async () => {
  'use strict';
  const status = document.getElementById('cryptoStatus');
  if (!status) return;
  const chat = document.getElementById('chatApp');
  const localKeyName = 'bakir-messenger-p256-key-v2-' + status.dataset.identity;
  if (!status.dataset.identity) { status.textContent = 'Messenger identity unavailable.'; return; }
  const enc = new TextEncoder();
  const dec = new TextDecoder();
  const toB64 = (buf) => btoa(String.fromCharCode(...new Uint8Array(buf)));
  const fromB64 = (s) => Uint8Array.from(atob(s), c => c.charCodeAt(0));
  const csrf = document.querySelector('meta[name="_csrf"]')?.content || '';
  const csrfHeader = {'X-CSRF-TOKEN': csrf};
  const serverKey = status.dataset.serverKey || '';
  const setStatus = (text) => { status.textContent = text; };
  let privateKey;
  let publicKey;
  let sharedKey;
  let socket;
  let socketReady = false;

  function openKeysDb() {
    return new Promise((resolve,reject) => {
      const req = indexedDB.open('bakir-messenger-keys', 1);
      req.onupgradeneeded = () => req.result.createObjectStore('keys');
      req.onsuccess = () => resolve(req.result);
      req.onerror = () => reject(req.error);
    });
  }
  async function readStoredKey() {
    const db = await openKeysDb();
    return new Promise((resolve,reject) => {
      const tx = db.transaction('keys','readonly');
      const req = tx.objectStore('keys').get(localKeyName);
      req.onsuccess = () => resolve(req.result);
      req.onerror = () => reject(req.error);
      tx.oncomplete = () => db.close();
    });
  }
  async function storeKey(obj) {
    const db = await openKeysDb();
    return new Promise((resolve,reject) => {
      const tx = db.transaction('keys','readwrite');
      tx.objectStore('keys').put(obj,localKeyName);
      tx.oncomplete = () => { db.close();resolve(); };
      tx.onerror = () => reject(tx.error);
    });
  }
  async function uploadKey(spki,reset=false) {
    const data = new URLSearchParams({publicKey:spki,reset:String(reset)});
    const response = await fetch(window.bakirTabUrl('/messenger/keys'),{
      method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded',...csrfHeader},
      body:data.toString(),credentials:'same-origin'
    });
    if (!response.ok) throw new Error('Public-key registration failed');
  }
  async function initializeKeys(reset=false) {
    if (!crypto.subtle || !indexedDB) throw new Error('A modern HTTPS browser is required for encryption.');
    let saved = reset ? null : await readStoredKey();
    if (!saved && serverKey && !reset) {
      throw new Error('This account already has a key on a different browser. Use the original browser or explicitly reset. Old chats will not decrypt after reset.');
    }
    if (!saved) {
      const pair = await crypto.subtle.generateKey({name:'ECDH',namedCurve:'P-256'},true,['deriveKey']);
      saved = {
        privateKey:toB64(await crypto.subtle.exportKey('pkcs8',pair.privateKey)),
        publicKey:toB64(await crypto.subtle.exportKey('spki',pair.publicKey))
      };
      await uploadKey(saved.publicKey,reset);
      await storeKey(saved);
    } else if (!serverKey) {
      await uploadKey(saved.publicKey,false);
    } else if (serverKey !== saved.publicKey) {
      throw new Error('Server public key differs from this browser key. Refusing to send.');
    }
    publicKey=saved.publicKey;
    privateKey=await crypto.subtle.importKey('pkcs8',fromB64(saved.privateKey),
      {name:'ECDH',namedCurve:'P-256'},false,['deriveKey']);
    const fp=toB64(await crypto.subtle.digest('SHA-256',fromB64(publicKey))).substring(0,24);
    setStatus('Client encryption ready · your key fingerprint: '+fp);
    return saved;
  }
  document.getElementById('resetKey')?.addEventListener('click',async () => {
    if (!confirm('Reset encryption keys? Historical encrypted messages will become unreadable.')) return;
    try { await initializeKeys(true);setStatus('Encryption key reset. Refresh conversations.'); }
    catch (err) { setStatus('Key reset failed: '+err.message); }
  });
  try { await initializeKeys(); }
  catch(err) { setStatus(err.message); return; }
  if (!chat) return;
  if (chat.dataset.blocked === 'true') { setStatus('Messaging is blocked for this contact.'); return; }
  const peerKeyB64 = chat.dataset.peerKey;
  if (!peerKeyB64) { setStatus('Your contact has not initialized encryption on their device yet.');return; }
  try {
    const peerPublicKey=await crypto.subtle.importKey('spki',fromB64(peerKeyB64),
      {name:'ECDH',namedCurve:'P-256'},false,[]);
    sharedKey=await crypto.subtle.deriveKey({name:'ECDH',public:peerPublicKey},privateKey,
      {name:'AES-GCM',length:256},false,['encrypt','decrypt']);
    const peerFp=toB64(await crypto.subtle.digest('SHA-256',fromB64(peerKeyB64))).substring(0,24);
    setStatus('Encrypted chat ready · contact key fingerprint: '+peerFp);
  } catch(err) { setStatus('Cannot load contact encryption key: '+err.message);return; }

  const me = String(chat.dataset.self);
  const history = document.getElementById('chatHistory');
  async function decrypt(ciphertext,iv) {
    const data=await crypto.subtle.decrypt({name:'AES-GCM',iv:fromB64(iv)},sharedKey,fromB64(ciphertext));
    return dec.decode(data);
  }
  async function display(m,existing=null) {
    const node=existing || document.createElement('div');
    node.className='encrypted-message';
    node.style.cssText='padding:10px;margin:6px 0;border-radius:8px;background:white;white-space:pre-wrap;overflow-wrap:anywhere';
    const sender=String(m.senderId)===me?'You: ':'Contact: ';
    try { node.textContent=sender+await decrypt(m.ciphertext,m.iv); }
    catch(e) { node.textContent=sender+'[Encrypted message cannot be opened on this device]'; }
    if (!existing) history.appendChild(node);
    history.scrollTop=history.scrollHeight;
  }
  for(const el of history.querySelectorAll('.encrypted-message')) {
    await display({senderId:el.dataset.sender,iv:el.dataset.iv,ciphertext:el.dataset.ciphertext},el);
  }
  const wsProto=location.protocol==='https:'?'wss:':'ws:';
  socket=new WebSocket(`${wsProto}//${location.host}${window.bakirTabUrl('/ws')}`);
  socket.onopen=()=>socket.send('CONNECT\naccept-version:1.2\nhost:'+location.host+'\nheart-beat:0,0\n\n\0');
  socket.onmessage=async (event)=>{
    for(const raw of event.data.split('\0')) {
      const frame=raw.trim();
      if(frame.startsWith('CONNECTED')) {
        socket.send('SUBSCRIBE\nid:encrypted-messages\ndestination:/user/queue/messages\n\n\0');
        socketReady=true;
        document.getElementById('chatSend').disabled=false;
      } else if(frame.startsWith('MESSAGE')) {
        const pos=raw.indexOf('\n\n');
        if(pos<0)continue;
        try {const m=JSON.parse(raw.substring(pos+2));if(m.conversationId===chat.dataset.conversation) await display(m);}
        catch(e){console.warn('Malformed encrypted message notification');}
      } else if(frame.startsWith('ERROR')) {
        socketReady=false;setStatus('WebSocket authorization failed; reload after logging in.');
      }
    }
  };
  socket.onclose=()=>{socketReady=false;document.getElementById('chatSend').disabled=true;};
  document.getElementById('chatForm').addEventListener('submit',async (event)=>{
    event.preventDefault();
    const input=document.getElementById('chatInput');
    const text=input.value.trim();
    if(!text || !socketReady) return;
    const iv=crypto.getRandomValues(new Uint8Array(12));
    const ciphertext=await crypto.subtle.encrypt({name:'AES-GCM',iv},sharedKey,enc.encode(text));
    const payload={conversationId:chat.dataset.conversation,clientMessageId:crypto.randomUUID(),
      ciphertext:toB64(ciphertext),iv:toB64(iv)};
    socket.send('SEND\ndestination:/app/messages.send\ncontent-type:application/json\n\n'+JSON.stringify(payload)+'\0');
    input.value='';
  });
})();
