package com.routeonomy.app.domain;
import org.junit.Test;
import static org.junit.Assert.*;
public class DriveSessionTest {
    @Test public void parkDoesNotMeanArrived(){DriveSession s=new DriveSession();assertFalse(s.canStartQuiz());s.setGear(DriveSession.Gear.DRIVE);s.setGear(DriveSession.Gear.PARK);assertFalse(s.canStartQuiz());s.setArrival(DriveSession.Arrival.ARRIVED);assertTrue(s.canStartQuiz());}
    @Test public void etaHasPositiveMinimum(){DriveSession s=new DriveSession();s.setEtaMinutes(0);assertEquals(1,s.etaMinutes());}
}
