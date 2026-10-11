package de.gigaworks.seatbidding.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

public record ConfirmedRevisionRequest(@JsonAlias("stateRevision") long revision, boolean confirmed) {
}