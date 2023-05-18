package com.veraxsystems.vxipmi.controller;


import com.veraxsystems.vxipmi.api.async.ConnectionHandle;
import com.veraxsystems.vxipmi.api.sync.IpmiConnector;
import com.veraxsystems.vxipmi.coding.commands.IpmiVersion;
import com.veraxsystems.vxipmi.coding.commands.PrivilegeLevel;
import com.veraxsystems.vxipmi.coding.commands.chassis.*;
import com.veraxsystems.vxipmi.coding.commands.session.SetSessionPrivilegeLevel;
import com.veraxsystems.vxipmi.coding.protocol.AuthenticationType;
import com.veraxsystems.vxipmi.coding.security.CipherSuite;
import com.veraxsystems.vxipmi.common.DemoVo;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.InetAddress;

@RestController
@RequestMapping("/ipmi")
public class IpmiController {

    @RequestMapping(value = "/post", method = {RequestMethod.POST})
    public Object post(@RequestBody DemoVo vo) throws Exception {
        IpmiConnector connector;

        // Create the connector, specify port that will be used to communicate
        // with the remote host. The UDP layer starts listening at this port, so
        // no 2 connectors can work at the same time on the same port.
        connector = new IpmiConnector(vo.getPort());
        System.out.println("Connector created");

        // Create the connection and get the handle, specify IP address of the
        // remote host. The connection is being registered in ConnectionManager,
        // the handle will be needed to identify it among other connections
        // (target IP address isn't enough, since we can handle multiple
        // connections to the same host)
        ConnectionHandle handle = connector.createConnection(InetAddress.getByName(vo.getIp()));
        System.out.println("Connection created");

        // Get available cipher suites list via getAvailableCipherSuites and
        // pick one of them that will be used further in the session.
        CipherSuite cs = connector.getAvailableCipherSuites(handle).get(3);
        System.out.println("Cipher suite picked");

        // Provide chosen cipher suite and privilege level to the remote host.
        // From now on, your connection handle will contain these information.
        connector.getChannelAuthenticationCapabilities(handle, cs, PrivilegeLevel.Administrator);
        System.out.println("Channel authentication capabilities receivied");

        // Start the session, provide username and password, and optionally the
        // BMC key (only if the remote host has two-key authentication enabled,
        // otherwise this parameter should be null)
        connector.openSession(handle, vo.getUser(), vo.getPass(), null);
        System.out.println("Session open");

        // Send some message and read the response
        GetChassisStatusResponseData rd = (GetChassisStatusResponseData) connector.sendMessage(handle,
                new GetChassisStatus(IpmiVersion.V20, cs, AuthenticationType.RMCPPlus));

        System.out.println("Received answer");
        System.out.println("System power state is " + (rd.isPowerOn() ? "up" : "down"));

        // Set session privilege level to administrator, as ChassisControl command requires this level
        connector.sendMessage(handle, new SetSessionPrivilegeLevel(IpmiVersion.V20, cs, AuthenticationType.RMCPPlus,
                PrivilegeLevel.Administrator));

        ChassisControl chassisControl = null;

        //Power on or off
        if (!rd.isPowerOn()) {
            chassisControl = new ChassisControl(IpmiVersion.V20, cs, AuthenticationType.RMCPPlus, PowerCommand.PowerUp);
        } else {
            chassisControl = new ChassisControl(IpmiVersion.V20, cs, AuthenticationType.RMCPPlus,
                    PowerCommand.PowerDown);
        }

        ChassisControlResponseData data = (ChassisControlResponseData) connector.sendMessage(handle, chassisControl);

        // Close the session
        connector.closeSession(handle);
        System.out.println("Session closed");

        // Close connection manager and release the listener port.
        connector.tearDown();
        System.out.println("Connection manager closed");

        return 1;
    }

}
