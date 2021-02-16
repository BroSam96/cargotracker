package org.eclipse.cargotracker.application;

import org.apache.commons.lang3.time.DateUtils;
import org.eclipse.cargotracker.application.internal.DefaultBookingService;
import org.eclipse.cargotracker.application.util.DateUtil;
import org.eclipse.cargotracker.application.util.RestConfiguration;
import org.eclipse.cargotracker.domain.model.cargo.*;
import org.eclipse.cargotracker.domain.model.handling.*;
import org.eclipse.cargotracker.domain.model.location.Location;
import org.eclipse.cargotracker.domain.model.location.LocationRepository;
import org.eclipse.cargotracker.domain.model.location.SampleLocations;
import org.eclipse.cargotracker.domain.model.location.UnLocode;
import org.eclipse.cargotracker.domain.model.voyage.*;
import org.eclipse.cargotracker.domain.service.RoutingService;
import org.eclipse.cargotracker.domain.shared.*;
import org.eclipse.cargotracker.infrastructure.logging.LoggerProducer;
import org.eclipse.cargotracker.infrastructure.persistence.jpa.JpaCargoRepository;
import org.eclipse.cargotracker.infrastructure.persistence.jpa.JpaHandlingEventRepository;
import org.eclipse.cargotracker.infrastructure.persistence.jpa.JpaLocationRepository;
import org.eclipse.cargotracker.infrastructure.persistence.jpa.JpaVoyageRepository;
import org.eclipse.cargotracker.infrastructure.routing.ExternalRoutingService;
import org.eclipse.pathfinder.api.GraphTraversalService;
import org.eclipse.pathfinder.api.TransitEdge;
import org.eclipse.pathfinder.api.TransitPath;
import org.eclipse.pathfinder.internal.GraphDao;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.junit.InSequence;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.jboss.shrinkwrap.resolver.api.maven.Maven;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.*;

/**
 * Application layer integration test covering a number of otherwise fairly trivial components that
 * largely do not warrant their own tests.
 *
 * <p>Ensure a Payara instance is running locally before this test is executed, with the default
 * user name and password.
 */
// TODO [Jakarta EE 8] Move to the Java Date-Time API for date manipulation. Also avoid hard-coded
// dates.
@RunWith(Arquillian.class)
public class BookingServiceTest {

  @Inject private BookingService bookingService;
  @PersistenceContext private EntityManager entityManager;

  private static TrackingId trackingId;
  private static List<Itinerary> candidates;
  private static Date deadline;
  private static Itinerary assigned;

  @Deployment
  public static WebArchive createDeployment() {
    WebArchive war =
        ShrinkWrap.create(WebArchive.class, "cargo-tracker-test.war")
            // Application layer component directly under test.
            .addClass(BookingService.class)
            // Domain layer components.
            .addClass(TrackingId.class)
            .addClass(UnLocode.class)
            .addClass(Itinerary.class)
            .addClass(Leg.class)
            .addClass(Voyage.class)
            .addClass(VoyageNumber.class)
            .addClass(Schedule.class)
            .addClass(CarrierMovement.class)
            .addClass(Location.class)
            .addClass(HandlingEvent.class)
            .addClass(Cargo.class)
            .addClass(RouteSpecification.class)
            .addClass(AbstractSpecification.class)
            .addClass(Specification.class)
            .addClass(AndSpecification.class)
            .addClass(OrSpecification.class)
            .addClass(NotSpecification.class)
            .addClass(Delivery.class)
            .addClass(TransportStatus.class)
            .addClass(HandlingActivity.class)
            .addClass(RoutingStatus.class)
            .addClass(HandlingHistory.class)
            .addClass(DomainObjectUtils.class)
            .addClass(CargoRepository.class)
            .addClass(LocationRepository.class)
            .addClass(VoyageRepository.class)
            .addClass(HandlingEventRepository.class)
            .addClass(HandlingEventFactory.class)
            .addClass(CannotCreateHandlingEventException.class)
            .addClass(UnknownCargoException.class)
            .addClass(UnknownVoyageException.class)
            .addClass(UnknownLocationException.class)
            .addClass(RoutingService.class)
            // Application layer components
            .addClass(DefaultBookingService.class)
            // Infrastructure layer components.
            .addClass(JpaCargoRepository.class)
            .addClass(JpaVoyageRepository.class)
            .addClass(JpaHandlingEventRepository.class)
            .addClass(JpaLocationRepository.class)
            .addClass(ExternalRoutingService.class)
            .addClass(LoggerProducer.class)
            // Interface components
            .addClass(TransitPath.class)
            .addClass(TransitEdge.class)
            // Third-party system simulator
            .addClass(GraphTraversalService.class)
            .addClass(GraphDao.class)
            // Sample data.
            .addClass(BookingServiceTestDataGenerator.class)
            .addClass(SampleLocations.class)
            .addClass(SampleVoyages.class)
            .addClass(DateUtil.class)
            .addClass(RestConfiguration.class)
            .addAsResource("META-INF/persistence.xml", "META-INF/persistence.xml");

    war.addAsWebInfResource("test-web.xml", "web.xml");

    war.addAsLibraries(
        Maven.resolver()
            .loadPomFromFile("pom.xml")
            .resolve("org.apache.commons:commons-lang3")
            .withTransitivity()
            .asFile());

    return war;
  }

  @Test
  @InSequence(1)
  public void testRegisterNew() {
    UnLocode fromUnlocode = new UnLocode("USCHI");
    UnLocode toUnlocode = new UnLocode("SESTO");

    deadline = new Date();
    GregorianCalendar calendar = new GregorianCalendar();
    calendar.setTime(deadline);
    calendar.add(Calendar.MONTH, 6); // Six months ahead.
    deadline.setTime(calendar.getTime().getTime());

    trackingId = bookingService.bookNewCargo(fromUnlocode, toUnlocode, deadline);

    Cargo cargo =
        entityManager
            .createNamedQuery("Cargo.findByTrackingId", Cargo.class)
            .setParameter("trackingId", trackingId)
            .getSingleResult();

    Assertions.assertEquals(SampleLocations.CHICAGO, cargo.getOrigin());
    Assertions.assertEquals(SampleLocations.STOCKHOLM, cargo.getRouteSpecification().getDestination());
    Assertions.assertTrue(DateUtils.isSameDay(deadline, cargo.getRouteSpecification().getArrivalDeadline()));
    Assertions.assertEquals(TransportStatus.NOT_RECEIVED, cargo.getDelivery().getTransportStatus());
    Assertions.assertEquals(Location.UNKNOWN, cargo.getDelivery().getLastKnownLocation());
    Assertions.assertEquals(Voyage.NONE, cargo.getDelivery().getCurrentVoyage());
    Assertions.assertFalse(cargo.getDelivery().isMisdirected());
    Assertions.assertEquals(Delivery.ETA_UNKOWN, cargo.getDelivery().getEstimatedTimeOfArrival());
    Assertions.assertEquals(Delivery.NO_ACTIVITY, cargo.getDelivery().getNextExpectedActivity());
    Assertions.assertFalse(cargo.getDelivery().isUnloadedAtDestination());
    Assertions.assertEquals(RoutingStatus.NOT_ROUTED, cargo.getDelivery().getRoutingStatus());
    Assertions.assertEquals(Itinerary.EMPTY_ITINERARY, cargo.getItinerary());
  }

  @Test
  @InSequence(2)
  public void testRouteCandidates() {
    candidates = bookingService.requestPossibleRoutesForCargo(trackingId);

    Assertions.assertFalse(candidates.isEmpty());
  }

  @Test
  @InSequence(3)
  public void testAssignRoute() {
    assigned = candidates.get(new Random().nextInt(candidates.size()));

    bookingService.assignCargoToRoute(assigned, trackingId);

    Cargo cargo =
        entityManager
            .createNamedQuery("Cargo.findByTrackingId", Cargo.class)
            .setParameter("trackingId", trackingId)
            .getSingleResult();

    Assertions.assertEquals(assigned, cargo.getItinerary());
    Assertions.assertEquals(TransportStatus.NOT_RECEIVED, cargo.getDelivery().getTransportStatus());
    Assertions.assertEquals(Location.UNKNOWN, cargo.getDelivery().getLastKnownLocation());
    Assertions.assertEquals(Voyage.NONE, cargo.getDelivery().getCurrentVoyage());
    Assertions.assertFalse(cargo.getDelivery().isMisdirected());
    Assertions.assertTrue(cargo.getDelivery().getEstimatedTimeOfArrival().before(deadline));
    Assertions.assertEquals(
        HandlingEvent.Type.RECEIVE, cargo.getDelivery().getNextExpectedActivity().getType());
    Assertions.assertEquals(
        SampleLocations.CHICAGO, cargo.getDelivery().getNextExpectedActivity().getLocation());
    Assertions.assertNull(cargo.getDelivery().getNextExpectedActivity().getVoyage());
    Assertions.assertFalse(cargo.getDelivery().isUnloadedAtDestination());
    Assertions.assertEquals(RoutingStatus.ROUTED, cargo.getDelivery().getRoutingStatus());
  }

  @Test
  @InSequence(4)
  public void testChangeDestination() {
    bookingService.changeDestination(trackingId, new UnLocode("FIHEL"));

    Cargo cargo =
        entityManager
            .createNamedQuery("Cargo.findByTrackingId", Cargo.class)
            .setParameter("trackingId", trackingId)
            .getSingleResult();

    Assertions.assertEquals(SampleLocations.CHICAGO, cargo.getOrigin());
    Assertions.assertEquals(SampleLocations.HELSINKI, cargo.getRouteSpecification().getDestination());
    Assertions.assertTrue(DateUtils.isSameDay(deadline, cargo.getRouteSpecification().getArrivalDeadline()));
    Assertions.assertEquals(assigned, cargo.getItinerary());
    Assertions.assertEquals(TransportStatus.NOT_RECEIVED, cargo.getDelivery().getTransportStatus());
    Assertions.assertEquals(Location.UNKNOWN, cargo.getDelivery().getLastKnownLocation());
    Assertions.assertEquals(Voyage.NONE, cargo.getDelivery().getCurrentVoyage());
    Assertions.assertFalse(cargo.getDelivery().isMisdirected());
    Assertions.assertEquals(Delivery.ETA_UNKOWN, cargo.getDelivery().getEstimatedTimeOfArrival());
    Assertions.assertEquals(Delivery.NO_ACTIVITY, cargo.getDelivery().getNextExpectedActivity());
    Assertions.assertFalse(cargo.getDelivery().isUnloadedAtDestination());
    Assertions.assertEquals(RoutingStatus.MISROUTED, cargo.getDelivery().getRoutingStatus());
  }

  @Test
  @InSequence(5)
  public void testChangeDeadline() {
    Calendar cal = Calendar.getInstance();
    cal.setTime(deadline);
    cal.add(Calendar.MONTH, 1); // Change the deadline one month ahead of the original
    Date newDeadline = cal.getTime();
    bookingService.changeDeadline(trackingId, newDeadline);

    Cargo cargo =
        entityManager
            .createNamedQuery("Cargo.findByTrackingId", Cargo.class)
            .setParameter("trackingId", trackingId)
            .getSingleResult();

    Assertions.assertEquals(SampleLocations.CHICAGO, cargo.getOrigin());
    Assertions.assertEquals(SampleLocations.HELSINKI, cargo.getRouteSpecification().getDestination());
    Assertions.assertTrue(
        DateUtils.isSameDay(newDeadline, cargo.getRouteSpecification().getArrivalDeadline()));
    Assertions.assertEquals(assigned, cargo.getItinerary());
    Assertions.assertEquals(TransportStatus.NOT_RECEIVED, cargo.getDelivery().getTransportStatus());
    Assertions.assertEquals(Location.UNKNOWN, cargo.getDelivery().getLastKnownLocation());
    Assertions.assertEquals(Voyage.NONE, cargo.getDelivery().getCurrentVoyage());
    Assertions.assertFalse(cargo.getDelivery().isMisdirected());
    Assertions.assertEquals(Delivery.ETA_UNKOWN, cargo.getDelivery().getEstimatedTimeOfArrival());
    Assertions.assertEquals(Delivery.NO_ACTIVITY, cargo.getDelivery().getNextExpectedActivity());
    Assertions.assertFalse(cargo.getDelivery().isUnloadedAtDestination());
    Assertions.assertEquals(RoutingStatus.MISROUTED, cargo.getDelivery().getRoutingStatus());
  }
}
