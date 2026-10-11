package com.example.bakir_khata.receipts;

import com.example.bakir_khata.model.User;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Locale;

@Controller
@RequestMapping("/receipts")
@RequiredArgsConstructor
public class ReceiptController {
    private final ReceiptService receipts;
    private final ReceiptPdfRenderer renderer;
    private final ReceiptSigner signer;

    @GetMapping
    public String index(@AuthenticationPrincipal User user, Model model) {
        ReceiptService.Overview overview = receipts.overview(user);
        model.addAttribute("loanReceipts", overview.loans());
        model.addAttribute("paymentReceipts", overview.payments());
        return "receipts/index";
    }

    @GetMapping(value = "/loans/{id}.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> loan(@PathVariable Long id, @AuthenticationPrincipal User user) {
        return download(receipts.loanReceipt(id, user));
    }

    @GetMapping(value = "/payments/{id}.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> payment(@PathVariable Long id, @AuthenticationPrincipal User user) {
        return download(receipts.paymentReceipt(id, user));
    }

    @GetMapping(value = "/transactions/{id}.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> transaction(@PathVariable Long id, @AuthenticationPrincipal User user) {
        return download(receipts.transactionReceipt(id, user));
    }

    @GetMapping(value = "/loans/{id}/settlement.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> settlement(@PathVariable Long id, @AuthenticationPrincipal User user) {
        return download(receipts.settlementReceipt(id, user));
    }

    /** QR status is intentionally public but discloses no names, emails or amounts. */
    @GetMapping("/verify")
    public String verify(@RequestParam(required = false) String kind,
                         @RequestParam(required = false) Long id,
                         @RequestParam(required = false) String signature,
                         Model model, HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store, max-age=0");
        response.setHeader("X-Robots-Tag", "noindex, nofollow");
        ReceiptKind type = null;
        try { if (kind != null) type = ReceiptKind.valueOf(kind.toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ignored) { }
        boolean signatureValid = signer.matches(type, id, signature);
        String status = signatureValid ? receipts.publicVerificationStatus(type, id) : "INVALID SIGNATURE";
        boolean found = signatureValid && !status.equals("NOT FOUND")
                && !status.equals("NOT SETTLED") && !status.equals("NOT CONFIRMED");
        model.addAttribute("valid", found);
        model.addAttribute("reference", found ? type.reference(id) : "Unknown receipt");
        model.addAttribute("receiptStatus", found ? status : "Could not verify this reference");
        return "receipts/verify";
    }

    private ResponseEntity<byte[]> download(ReceiptDocument receipt) {
        String filename = receipt.reference() + ".pdf";
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.APPLICATION_PDF)
                .body(renderer.render(receipt));
    }
}
