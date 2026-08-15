package de.gigaworks.seatbidding.allocation;

import java.util.List;

public interface PairingRandomSelector {

    <T> RandomSelector.Draw<T> select(List<T> canonicalValues);

}