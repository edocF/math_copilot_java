package com.fu.math_copilot.service;



import com.fu.math_copilot.model.entity.Question;


public interface QuestionCacheService {
    Question getFromCache(Long questionId, QuestionLoader loader);

    void evict(Long questionId);

    @FunctionalInterface
    interface QuestionLoader {
        Question load(Long questionId);
    }
}
