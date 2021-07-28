package io.ably.lib.test.android;

import io.ably.lib.push.ActivationStateMachine.CalledActivate;
import io.ably.lib.push.ActivationStateMachine.CalledDeactivate;
import io.ably.lib.push.ActivationStateMachine.Deregistered;
import io.ably.lib.push.ActivationStateMachine.Event;
import io.ably.lib.push.ActivationStateMachine.GotDeviceRegistration;
import io.ably.lib.push.ActivationStateMachine.GotPushDeviceDetails;
import io.ably.lib.push.ActivationStateMachine.RegistrationSynced;
import io.ably.lib.push.ActivationStateMachine.GettingDeviceRegistrationFailed;
import io.ably.lib.push.ActivationStateMachine.GettingPushDeviceDetailsFailed;
import io.ably.lib.push.ActivationStateMachine.SyncRegistrationFailed;
import io.ably.lib.push.ActivationStateMachine.DeregistrationFailed;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class EventTest {

    @Test
    public void events_subclasses_correctly_constructed_by_name() throws ClassNotFoundException, InstantiationException {

        CalledActivate calledActivateEvent = new CalledActivate();
        Event calledActivateReconstructed = Event.constructEventByName(calledActivateEvent.getName());
        assertEquals(calledActivateEvent.getClass(), calledActivateReconstructed.getClass());

        CalledDeactivate calledDeactivateEvent = new CalledDeactivate();
        Event calledDeactivateReconstructed = Event.constructEventByName(calledDeactivateEvent.getName());
        assertEquals(calledDeactivateEvent.getClass(), calledDeactivateReconstructed.getClass());

        GotPushDeviceDetails gotPushDeviceDetailsEvent = new GotPushDeviceDetails();
        Event gotPushDeviceDetailsReconstructed = Event.constructEventByName(gotPushDeviceDetailsEvent.getName());
        assertEquals(gotPushDeviceDetailsEvent.getClass(), gotPushDeviceDetailsReconstructed.getClass());

        RegistrationSynced registrationSyncedEvent = new RegistrationSynced();
        Event registrationSyncedReconstructed = Event.constructEventByName(registrationSyncedEvent.getName());
        assertEquals(registrationSyncedEvent.getClass(), registrationSyncedReconstructed.getClass());

        Deregistered DeregisteredEvent = new Deregistered();
        Event DeregisteredReconstructed = Event.constructEventByName(DeregisteredEvent.getName());
        assertEquals(DeregisteredEvent.getClass(), DeregisteredReconstructed.getClass());
    }

    @Test
    public void events_with_constructor_parameter_cannot_be_restored() {
        GotDeviceRegistration gotDeviceRegistration = new GotDeviceRegistration(null);
        try{
            Event.constructEventByName(gotDeviceRegistration.getName());
        } catch (Exception e) {
            assertEquals(InstantiationException.class, e.getClass());
        }

        GettingDeviceRegistrationFailed gettingDeviceRegistrationFailed = new GettingDeviceRegistrationFailed(null);
        try {
            Event.constructEventByName(gettingDeviceRegistrationFailed.getName());
        } catch (Exception e) {
            assertEquals(InstantiationException.class, e.getClass());
        }

        GettingPushDeviceDetailsFailed gettingPushDeviceDetailsFailed = new GettingPushDeviceDetailsFailed(null);
        try {
            Event.constructEventByName(gettingPushDeviceDetailsFailed.getName());
        } catch (Exception e) {
            assertEquals(InstantiationException.class, e.getClass());
        }

        SyncRegistrationFailed syncRegistrationFailed = new SyncRegistrationFailed(null);
        try {
            Event.constructEventByName(syncRegistrationFailed.getName());
        } catch (Exception e) {
            assertEquals(InstantiationException.class, e.getClass());
        }

        DeregistrationFailed deregistrationFailed = new DeregistrationFailed(null);
        try {
            Event.constructEventByName(deregistrationFailed.getName());
        } catch (Exception e) {
            assertEquals(InstantiationException.class, e.getClass());
        }
    }

    @Test(expected = ClassNotFoundException.class)
    public void unknown_events_cannot_be_constructed_by_name() throws Exception {
        Event.constructEventByName("notDefinedName");
    }
}
