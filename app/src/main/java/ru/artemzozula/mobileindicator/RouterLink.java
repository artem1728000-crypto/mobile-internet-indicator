package ru.artemzozula.mobileindicator;

import android.content.Intent;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.RouteInfo;
import android.net.Uri;

import java.net.Inet4Address;
import java.net.InetAddress;

record RouterLink(String address, String gateway) {
    static RouterLink from(LinkProperties properties) {
        if (properties == null) return new RouterLink("", "");
        String address = "";
        for (LinkAddress link : properties.getLinkAddresses()) {
            InetAddress ip = link.getAddress();
            if (ip instanceof Inet4Address && !ip.isLinkLocalAddress() && !ip.isLoopbackAddress()
                    && !ip.isAnyLocalAddress()) {
                address = ip.getHostAddress();
                break;
            }
        }
        for (RouteInfo route : properties.getRoutes()) {
            InetAddress ip = route.getGateway();
            if (route.isDefaultRoute() && ip instanceof Inet4Address && !ip.isAnyLocalAddress()
                    && !ip.isLoopbackAddress() && !ip.isMulticastAddress()
                    && !address.isEmpty() && !address.equals(ip.getHostAddress())) {
                return new RouterLink(address, ip.getHostAddress());
            }
        }
        return new RouterLink(address, "");
    }

    Intent browserIntent() {
        return new Intent(Intent.ACTION_VIEW, Uri.parse("http://" + gateway + "/"))
                .addCategory(Intent.CATEGORY_BROWSABLE);
    }
}
