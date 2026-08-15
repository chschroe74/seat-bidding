package de.gigaworks.seatbidding.notification;

import jakarta.enterprise.context.ApplicationScoped;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;

@ApplicationScoped
public class SystemEndpointAddressResolver implements EndpointAddressResolver {

    @Override
    public List<InetAddress> resolve(String host) throws UnknownHostException {
        return Arrays.asList(InetAddress.getAllByName(host));
    }

}