package org.sainm.schemapilot.app.review;

import org.sainm.schemapilot.report.ReportRepository;
import org.sainm.schemapilot.review.InMemoryReviewRepository;
import org.sainm.schemapilot.review.ReviewRepository;
import org.sainm.schemapilot.review.ReviewService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class ReviewModuleConfiguration {

    @Bean
    ReviewRepository reviewRepository() {
        return new InMemoryReviewRepository();
    }

    @Bean
    ReviewService reviewService(ReviewRepository reviewRepository, ReportRepository reportRepository) {
        return new ReviewService(reviewRepository, reportRepository);
    }
}
