package com.example.bakir_khata.service;

import com.example.bakir_khata.dto.UserRatingDTO;
import com.example.bakir_khata.model.User;

public interface RatingService {
    UserRatingDTO calculateRating(User user);
}
