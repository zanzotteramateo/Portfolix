package com.portfolix.api.transaction;

import com.portfolix.api.asset.Asset;
import com.portfolix.api.common.Currency;
import com.portfolix.api.portfolio.Portfolio;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "transactions")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Se puede cambiar: al editar la transacción, o al eliminar un portafolio moviendo sus transacciones a otro.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "portfolio_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Portfolio portfolio;

    /** Sin setter: se cambia solo con {@link #update}, que copia también su moneda. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "asset_id", nullable = false)
    @Setter(AccessLevel.NONE)
    private Asset asset;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 4)
    private TransactionType type;

    @Column(nullable = false, precision = 38, scale = 18)
    private BigDecimal quantity;

    /** Precio unitario. El monto total (cantidad × precio) se calcula, no se guarda. */
    @Column(nullable = false, precision = 38, scale = 18)
    private BigDecimal price;

    /** Se copia de la moneda del activo al crear o editar la transacción. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 3)
    @Setter(AccessLevel.NONE)
    private Currency currency;

    @Column(name = "trade_date", nullable = false)
    private LocalDate tradeDate;

    @Column(length = 500)
    private String notes;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public Transaction(Portfolio portfolio, Asset asset, TransactionType type,
                       BigDecimal quantity, BigDecimal price, LocalDate tradeDate, String notes) {
        this.portfolio = portfolio;
        this.asset = asset;
        this.currency = asset.getCurrency();
        this.type = type;
        this.quantity = quantity;
        this.price = price;
        this.tradeDate = tradeDate;
        this.notes = notes;
    }

    /** Reemplaza todos los datos (edición). La moneda se vuelve a copiar del activo, que puede haber cambiado. */
    public void update(Portfolio portfolio, Asset asset, TransactionType type,
                       BigDecimal quantity, BigDecimal price, LocalDate tradeDate, String notes) {
        this.portfolio = portfolio;
        this.asset = asset;
        this.currency = asset.getCurrency();
        this.type = type;
        this.quantity = quantity;
        this.price = price;
        this.tradeDate = tradeDate;
        this.notes = notes;
    }
}
