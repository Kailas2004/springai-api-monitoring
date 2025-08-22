package com.springai.api_monitoring.service;

import com.springai.api_monitoring.model.Anomalies;
import com.springai.api_monitoring.model.Metrics;
import com.springai.api_monitoring.repository.AnomaliesRepository;
import com.springai.api_monitoring.repository.MetricsRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AnomalyDetectionService {

    private final MetricsRepository metricsRepository;
    private final AnomaliesRepository anomaliesRepository;
    private final NotificationService notificationService;
    private static final Logger logger = LoggerFactory.getLogger(AnomalyDetectionService.class);

    public AnomalyDetectionService(
            MetricsRepository metricsRepository,
            AnomaliesRepository anomaliesRepository,
            NotificationService notificationService) {
        this.metricsRepository = metricsRepository;
        this.anomaliesRepository = anomaliesRepository;
        this.notificationService = notificationService;
    }

    // Main detection logic, manual or scheduled
    public int detectAnomalies() {
        List<Metrics> metricsList = metricsRepository.findAll();
        int count = 0;
        for (Metrics metric : metricsList) {
            boolean isAnomaly = false;
            StringBuilder type = new StringBuilder();
            String severity = "";

            if (metric.getErrorRate() != null && metric.getErrorRate() > 0.1) {
                isAnomaly = true;
                type.append("High Error Rate");
                severity = "CRITICAL";
            }
            if (metric.getResponseTime() != null && metric.getResponseTime() > 800.0) {
                if (isAnomaly) type.append("; ");
                isAnomaly = true;
                type.append("Slow Response Time");
                if (!"CRITICAL".equals(severity)) {
                    severity = "WARNING";  // choose higher if any metric is critical
                }
            }
            if (isAnomaly) {
                String typeString = type.toString().trim();
                // Deduplication: only insert if not already present
                boolean exists = anomaliesRepository.findByMetricIdAndType(metric.getId(), typeString).isPresent();
                if (!exists) {
                    Anomalies anomaly = Anomalies.builder()
                            .api(metric.getApi())
                            .metric(metric)
                            .type(typeString)
                            .severity(severity)
                            .detectedAt(LocalDateTime.now())
                            .build();
                    anomaliesRepository.save(anomaly);
                    notificationService.notifyAnomaly(anomaly); // <-- integrated notification
                    count++;
                }
            }
        }
        return count;
    }

    // Automatically run detection every 5 minutes
    @Scheduled(fixedRate = 300000)
    public void scheduledDetection() {
        int count = detectAnomalies();
        if (count > 0) {
            logger.info(count + " anomalies detected automatically at " + LocalDateTime.now());
        }
    }
}
