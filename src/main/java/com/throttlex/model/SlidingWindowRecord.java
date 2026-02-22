package com.throttlex.model;

import javax.persistence.*;
import lombok.*;


@Entity
@Table(
    name = "throttlex_sw_log",
    indexes = {
        @Index(name = "idx_sw_key_time", columnList = "key_id, request_time")
    }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SlidingWindowRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "key_id", nullable = false)
    private String keyId;

    @Column(name = "request_time", nullable = false)
    private long requestTime; // epoch millis
}
