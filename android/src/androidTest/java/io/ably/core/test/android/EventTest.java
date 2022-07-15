package io.ably.core.test.android;

import io.ably.core.push.ActivationStateMachine.CalledActivate;
import io.ably.core.push.ActivationStateMachine.CalledDeactivate;
import io.ably.core.push.ActivationStateMachine.Deregistered;
import io.ably.core.push.ActivationStateMachine.Event;
import io.ably.core.push.ActivationStateMachine.GotDeviceRegistration;
import io.ably.core.push.ActivationStateMachine.GotPushDeviceDetails;
import io.ably.core.push.ActivationStateMachine.RegistrationSynced;
import io.ably.core.push.ActivationStateMachine.GettingDeviceRegistrationFailed;
import io.ably.core.push.ActivationStateMachine.GettingPushDeviceDetailsFailed;
import io.ably.core.push.ActivationStateMachine.SyncRegistrationFailed;
import io.ably.core.push.ActivationStateMachine.DeregistrationFailed;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class EventTest {

    @Test
    public void events_subclasses_correctly_constructed_by_name() throws ClassNotFoundException, InstantiationException {

        CalledActivate calledActivateEvent = new CalledActivate();
        Event calledActivateReconstructed = Event.constructEventByName(calledActivateEvent.getPersistedName());
        assertEquals(calledActivateEvent.getClass(), calledActivateReconstructed.getClass());

        CalledDeactivate calledDeactivateEvent = new CalledDeactivate();
        Event calledDeactivateReconstructed = Event.constructEventByName(calledDeactivateEvent.getPersistedName());
        assertEquals(calledDeactivateEvent.getClass(), calledDeactivateReconstructed.getClass());

        GotPushDeviceDetails gotPushDeviceDetailsEvent = new GotPushDeviceDetails();
        Event gotPushDeviceDetailsReconstructed = Event.constructEventByName(gotPushDeviceDetailsEvent.getPersistedName());
        assertEquals(gotPushDeviceDetailsEvent.getClass(), gotPushDeviceDetailsReconstructed.getClass());

        RegistrationSynced registrationSyncedEvent = new RegistrationSynced();
        Event registrationSyncedReconstructed = Event.constructEventByName(registrationSyncedEvent.getPersistedName());
        assertEquals(registrationSyncedEvent.getClass(), registrationSyncedReconstructed.getClass());

        Deregistered DeregisteredEvent = new Deregistered();
        Event DeregisteredReconstructed = Event.constructEventByName(DeregisteredEvent.getPersistedName());
        assertEquals(DeregisteredEvent.getClass(), DeregisteredReconstructed.getClass());
    }

    @Test
    public void events_with_constructor_parameter_do_not_have_persisted_name() {
        assertNull(new GotDeviceRegistration(null).getPersistedName());
        assertNull(new GettingDeviceRegistrationFailed(null).getPersistedName());
        assertNull(new GettingPushDeviceDetailsFailed(null).getPersistedName());
        assertNull(new SyncRegistrationFailed(null).getPersistedName());
        assertNull(new DeregistrationFailed(null).getPersistedName());
    }

    @Test
    public void unknown_events_cannot_be_constructed_by_name() {
        assertNull(Event.constructEventByName("notDefinedName"));
    }
}
