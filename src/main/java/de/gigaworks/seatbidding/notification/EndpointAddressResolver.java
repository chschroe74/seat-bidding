package de.gigaworks.seatbidding.notification;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

public interface EndpointAddressResolver {

    List<InetAddress> resolve(String host) throws UnknownHostException;

}