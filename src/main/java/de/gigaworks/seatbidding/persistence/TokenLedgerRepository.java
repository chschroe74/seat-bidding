package de.gigaworks.seatbidding.persistence;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class TokenLedgerRepository implements PanacheRepositoryBase<TokenLedgerEntity, Long> {

}

