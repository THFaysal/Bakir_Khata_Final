package com.example.bakir_khata.dto;


import com.example.bakir_khata.model.enums.Relationship;
import jakarta.validation.constraints.*;
import org.springframework.web.multipart.MultipartFile;

public record LenderDTO(

        Long id,

        @NotBlank(message = "Name is required")
        @Size(max = 100, message = "Name cannot exceed 100 characters")
        String name,

        @NotBlank(message = "Phone is required")
        @Pattern(regexp = "^[0-9+\\-\\s]{7,20}$", message = "Phone must be numeric")
        String phone,

        @Email(message = "Enter a valid email address")
        String email,

        @Size(max = 255, message = "Address cannot exceed 255 characters")
        String address,

        @NotNull(message = "Relationship is required")
        Relationship relationship,

        @Size(max = 1000, message = "Notes cannot exceed 1000 characters")
        String notes,

        MultipartFile profileImage,

        String existingProfileImagePath
) {

    public LenderDTO() {
        this(null, null, null, null, null, null, null, null, null);
    }
}
