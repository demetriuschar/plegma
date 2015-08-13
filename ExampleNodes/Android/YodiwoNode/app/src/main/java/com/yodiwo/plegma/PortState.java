package com.yodiwo.plegma;

/**
 * Created by ApiGenerator Tool (Java) on 3/8/2015 10:26:23 &#956;&#956;.
 */

/**
 * internal state of a referenced Port
 */
public class PortState {
    /**
     * Yodiwo.API.Plegma.PortState.PortKey of the Yodiwo.API.Plegma.Port this message refers to (either generating the event, or receiving the event)
     */
    public String PortKey;
    /**
     * Contents of port in string form. See Yodiwo.API.Plegma.Port.State
     */
    public String State;
    /**
     * Revision number of this update; matches the Port State's internal sequence numbering. See Yodiwo.API.Plegma.Port.State
     */
    public int RevNum;
    /**
     * Denotes whether the Port that this message's PortKey refers to is currently deployed in any server graphs
     */
    public Boolean IsDeployed;

    public PortState() {
    }

    public PortState(String PortKey, String State, int RevNum, Boolean IsDeployed) {
        this.PortKey = PortKey;
        this.State = State;
        this.RevNum = RevNum;
        this.IsDeployed = IsDeployed;

    }

}