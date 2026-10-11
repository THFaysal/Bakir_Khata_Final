package com.example.bakir_khata.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private String safeReturnPath(HttpServletRequest request) {
        String referer=request.getHeader("Referer");
        if (referer==null) return "/dashboard";
        try {
            java.net.URI uri=java.net.URI.create(referer);
            if (uri.getHost()!=null && uri.getHost().equalsIgnoreCase(request.getServerName())
                    && uri.getRawPath()!=null && uri.getRawPath().startsWith("/")
                    && !uri.getRawPath().startsWith("//")) {
                return uri.getRawPath();
            }
        } catch (IllegalArgumentException ignored) { }
        return "/dashboard";
    }



    @ExceptionHandler(ResourceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ModelAndView handleNotFound(ResourceNotFoundException ex) {
        ModelAndView mav = new ModelAndView("error/404");
        mav.addObject("message", ex.getMessage());
        return mav;
    }

    @ExceptionHandler(BusinessRuleException.class)
    public String handleBusinessRule(BusinessRuleException ex, HttpServletRequest request,
                                      RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        return "redirect:" + safeReturnPath(request);
    }

    @ExceptionHandler({CounterpartyNotRegisteredException.class, InvalidTransactionException.class,
            UnauthorizedActionException.class})
    public String handleTransactionErrors(RuntimeException ex, HttpServletRequest request,
                                           RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        return "redirect:" + safeReturnPath(request);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String handleUploadTooLarge(HttpServletRequest request, RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("errorMessage", "Uploaded file is too large. Maximum allowed size is 5 MB.");
        return "redirect:" + safeReturnPath(request);
    }

    @ExceptionHandler(Exception.class)
    public ModelAndView handleGeneric(Exception ex) {
        log.error("Unexpected error", ex);
        ModelAndView mav = new ModelAndView("error/500");
        mav.addObject("message", "Something went wrong. Please try again.");
        return mav;
    }
}
