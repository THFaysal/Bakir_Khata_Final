package com.example.bakir_khata.service.impl;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.example.bakir_khata.exception.BusinessRuleException;
import com.example.bakir_khata.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class FileStorageServiceImpl implements FileStorageService {

    private final Cloudinary cloudinary;

    @Override
    public String store(
            MultipartFile file,
            String subDirectory
    ) {

        if (file == null || file.isEmpty()) {
            return null;
        }

        try {

            Map<?, ?> result =
                    cloudinary.uploader().upload(
                            file.getBytes(),
                            ObjectUtils.asMap(
                                    "folder",
                                    "bakir-khata/" + subDirectory,
                                    "resource_type",
                                    "auto"
                            )
                    );

            Object secureUrl =
                    result.get("secure_url");

            if (secureUrl == null) {
                throw new BusinessRuleException(
                        "Cloudinary did not return a file URL."
                );
            }

            return secureUrl.toString();

        } catch (Exception e) {

            throw new BusinessRuleException(
                    "Failed to upload file: "
                            + e.getMessage()
            );
        }
    }
}