document.addEventListener('DOMContentLoaded', function () {
    document.querySelectorAll('canvas[data-chart]').forEach(function (canvas) {
        const labels = JSON.parse(canvas.dataset.labels || '[]');
        const values = JSON.parse(canvas.dataset.values || '[]').map(Number);
        const type = canvas.dataset.chart || 'bar';
        const label = canvas.dataset.label || '';
        if (typeof Chart !== 'undefined') {
            new Chart(canvas, {
                type: type,
                data: { labels: labels, datasets: [{ label: label, data: values, borderWidth: 2, tension: 0.3, fill: false }] },
                options: {
                    responsive: true,
                    maintainAspectRatio: false,
                    plugins: { legend: { display: type === 'pie' || type === 'doughnut' } },
                    scales: (type === 'pie' || type === 'doughnut') ? {} : { y: { beginAtZero: true } }
                }
            });
        } else {
            drawFallback(canvas, labels, values, type);
        }
    });
});

function drawFallback(canvas, labels, values, type) {
    const rect = canvas.parentElement.getBoundingClientRect();
    const ratio = window.devicePixelRatio || 1;
    canvas.width = Math.max(320, rect.width) * ratio;
    canvas.height = Math.max(220, rect.height) * ratio;
    canvas.style.width = '100%';
    canvas.style.height = '100%';
    const ctx = canvas.getContext('2d');
    ctx.scale(ratio, ratio);
    const w = canvas.width / ratio, h = canvas.height / ratio;
    ctx.font = '12px Inter, sans-serif';
    ctx.fillStyle = '#6B7280';
    if (!values.length || values.every(v => v === 0)) {
        ctx.textAlign = 'center'; ctx.fillText('No chart data yet', w / 2, h / 2); return;
    }
    if (type === 'doughnut' || type === 'pie') return drawPie(ctx, w, h, labels, values, type === 'doughnut');
    const max = Math.max(...values, 1); const left = 42, right = 16, top = 18, bottom = 38;
    ctx.strokeStyle = '#E5E7EB'; ctx.beginPath(); ctx.moveTo(left, top); ctx.lineTo(left, h-bottom); ctx.lineTo(w-right, h-bottom); ctx.stroke();
    if (type === 'line') {
        ctx.strokeStyle = '#2563EB'; ctx.lineWidth = 2; ctx.beginPath();
        values.forEach((v,i)=>{const x=left+(w-left-right)*(values.length===1?0.5:i/(values.length-1));const y=h-bottom-(h-top-bottom)*(v/max);if(i===0)ctx.moveTo(x,y);else ctx.lineTo(x,y);ctx.fillStyle='#2563EB';ctx.fillRect(x-2,y-2,4,4);});ctx.stroke();
    } else {
        const gap=8, bw=(w-left-right-gap*(values.length-1))/values.length; values.forEach((v,i)=>{const bh=(h-top-bottom)*(v/max);const x=left+i*(bw+gap);ctx.fillStyle='#2563EB';ctx.fillRect(x,h-bottom-bh,bw,bh);});
    }
    ctx.textAlign='center'; labels.forEach((l,i)=>{const x=left+(w-left-right)*(values.length===1?0.5:i/(values.length-1));ctx.fillStyle='#6B7280';ctx.fillText(String(l).slice(0,10),x,h-12);});
}
function drawPie(ctx,w,h,labels,values,doughnut){const total=values.reduce((a,b)=>a+b,0);const cx=w*0.38,cy=h/2,r=Math.min(w*0.25,h*0.38);const palette=['#2563EB','#16A34A','#D97706','#DC2626','#7C3AED','#0891B2'];let angle=-Math.PI/2;values.forEach((v,i)=>{const next=angle+(v/total)*Math.PI*2;ctx.beginPath();ctx.moveTo(cx,cy);ctx.arc(cx,cy,r,angle,next);ctx.closePath();ctx.fillStyle=palette[i%palette.length];ctx.fill();angle=next;});if(doughnut){ctx.beginPath();ctx.arc(cx,cy,r*0.58,0,Math.PI*2);ctx.fillStyle='#FFFFFF';ctx.fill();}ctx.textAlign='left';labels.forEach((l,i)=>{ctx.fillStyle=palette[i%palette.length];ctx.fillRect(w*0.68,28+i*24,10,10);ctx.fillStyle='#374151';ctx.fillText(String(l)+' ('+values[i]+')',w*0.68+16,37+i*24);});}
