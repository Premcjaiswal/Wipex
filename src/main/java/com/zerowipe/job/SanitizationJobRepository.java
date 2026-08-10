package com.zerowipe.job;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SanitizationJobRepository extends JpaRepository<SanitizationJob, Long> {
}
