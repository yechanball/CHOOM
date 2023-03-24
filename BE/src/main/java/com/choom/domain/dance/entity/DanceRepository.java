package com.choom.domain.dance.entity;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DanceRepository extends JpaRepository<Dance, Long> {

   Optional<Dance> findByVideoPath(String videoPath);


}