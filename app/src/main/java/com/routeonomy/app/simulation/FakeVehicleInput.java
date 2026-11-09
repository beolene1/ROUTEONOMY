package com.routeonomy.app.simulation;
import com.routeonomy.app.domain.DriveSession;
/** UI-only demo adapter. */
public final class FakeVehicleInput {
    private final DriveSession session;
    public FakeVehicleInput(DriveSession session) { this.session = session; }
    public void setGear(DriveSession.Gear gear) { session.setGear(gear); }
    public void setEta(int minutes) { session.setEtaMinutes(minutes); }
    public void markArrived(boolean arrived) { session.setArrival(arrived ? DriveSession.Arrival.ARRIVED : DriveSession.Arrival.NOT_ARRIVED); }
}
