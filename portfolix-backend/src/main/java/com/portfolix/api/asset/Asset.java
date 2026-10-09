package com.portfolix.api.asset;

import com.portfolix.api.common.Currency;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "assets")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Asset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String symbol;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private AssetType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 3)
    private Currency currency;

    @Column(nullable = false)
    private boolean active = true;

    public Asset(String symbol, String name, AssetType type, Currency currency) {
        this.symbol = symbol;
        this.name = name;
        this.type = type;
        this.currency = currency;
    }
}
