package ua.tn.entity;

import lombok.EqualsAndHashCode;
import lombok.Setter;
import lombok.ToString;
import ua.tn.annotation.Column;
import ua.tn.annotation.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@ToString
@EqualsAndHashCode
@Setter
@Table("products")
public class Product {
    private Integer id;

    private String name;

    private BigDecimal price;

    @Column(value = "created_at")
    private LocalDateTime createdAt;
}
