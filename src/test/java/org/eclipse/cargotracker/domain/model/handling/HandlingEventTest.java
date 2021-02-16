package org.eclipse.cargotracker.domain.model.handling;

import org.eclipse.cargotracker.domain.model.cargo.Cargo;
import org.eclipse.cargotracker.domain.model.cargo.RouteSpecification;
import org.eclipse.cargotracker.domain.model.cargo.TrackingId;
import org.eclipse.cargotracker.domain.model.location.SampleLocations;
import org.eclipse.cargotracker.domain.model.voyage.SampleVoyages;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Date;

public class HandlingEventTest {

    private final TrackingId trackingId = new TrackingId("XYZ");
    private final RouteSpecification routeSpecification =
            new RouteSpecification(SampleLocations.HONGKONG, SampleLocations.NEWYORK, new Date());
    private final Cargo cargo = new Cargo(trackingId, routeSpecification);

    @Test
    public void testNewWithCarrierMovement() {
        HandlingEvent event1 =
                new HandlingEvent(
                        cargo,
                        new Date(),
                        new Date(),
                        HandlingEvent.Type.LOAD,
                        SampleLocations.HONGKONG,
                        SampleVoyages.CM003);
        Assertions.assertEquals(SampleLocations.HONGKONG, event1.getLocation());

        HandlingEvent event2 =
                new HandlingEvent(
                        cargo,
                        new Date(),
                        new Date(),
                        HandlingEvent.Type.UNLOAD,
                        SampleLocations.NEWYORK,
                        SampleVoyages.CM003);
        Assertions.assertEquals(SampleLocations.NEWYORK, event2.getLocation());

        // These event types prohibit a carrier movement association
        for (HandlingEvent.Type type :
                Arrays.asList(
                        HandlingEvent.Type.CLAIM,
                        HandlingEvent.Type.RECEIVE,
                        HandlingEvent.Type.CUSTOMS)) {
            try {
                new HandlingEvent(
                        cargo,
                        new Date(),
                        new Date(),
                        type,
                        SampleLocations.HONGKONG,
                        SampleVoyages.CM003);
                Assertions.fail("Handling event type " + type + " prohibits carrier movement");
            } catch (IllegalArgumentException expected) {
            }
        }

        // These event types requires a carrier movement association
        for (HandlingEvent.Type type :
                Arrays.asList(HandlingEvent.Type.LOAD, HandlingEvent.Type.UNLOAD)) {
            try {
                new HandlingEvent(
                        cargo, new Date(), new Date(), type, SampleLocations.HONGKONG, null);
                Assertions.fail("Handling event type " + type + " requires carrier movement");
            } catch (NullPointerException expected) {
            }
        }
    }

    @Test
    public void testNewWithLocation() {
        HandlingEvent event1 =
                new HandlingEvent(
                        cargo,
                        new Date(),
                        new Date(),
                        HandlingEvent.Type.CLAIM,
                        SampleLocations.HELSINKI);
        Assertions.assertEquals(SampleLocations.HELSINKI, event1.getLocation());
    }

    @Test
    public void testCurrentLocationLoadEvent() throws Exception {
        HandlingEvent event =
                new HandlingEvent(
                        cargo,
                        new Date(),
                        new Date(),
                        HandlingEvent.Type.LOAD,
                        SampleLocations.CHICAGO,
                        SampleVoyages.CM004);

        Assertions.assertEquals(SampleLocations.CHICAGO, event.getLocation());
    }

    @Test
    public void testCurrentLocationUnloadEvent() throws Exception {
        HandlingEvent ev =
                new HandlingEvent(
                        cargo,
                        new Date(),
                        new Date(),
                        HandlingEvent.Type.UNLOAD,
                        SampleLocations.HAMBURG,
                        SampleVoyages.CM004);

        Assertions.assertEquals(SampleLocations.HAMBURG, ev.getLocation());
    }

    @Test
    public void testCurrentLocationReceivedEvent() throws Exception {
        HandlingEvent event =
                new HandlingEvent(
                        cargo,
                        new Date(),
                        new Date(),
                        HandlingEvent.Type.RECEIVE,
                        SampleLocations.CHICAGO);

        Assertions.assertEquals(SampleLocations.CHICAGO, event.getLocation());
    }

    @Test
    public void testCurrentLocationClaimedEvent() throws Exception {
        HandlingEvent event =
                new HandlingEvent(
                        cargo,
                        new Date(),
                        new Date(),
                        HandlingEvent.Type.CLAIM,
                        SampleLocations.CHICAGO);

        Assertions.assertEquals(SampleLocations.CHICAGO, event.getLocation());
    }
}