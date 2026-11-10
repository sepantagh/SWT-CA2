package airplane;

import airplane.components.Engine;
import airplane.components.FuelSystem;
import airplane.components.LandingGear;
import airplane.components.NavigationSystem;
import airplane.exceptions.FlightException;
import airplane.models.FlightStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AircraftTest {

    private Engine engine;
    private FuelSystem fuelSystem;
    private LandingGear landingGear;
    private NavigationSystem navigationSystem;
    private Aircraft aircraft;

    @BeforeEach
    void setUp() {
        engine = mock(Engine.class);
        fuelSystem = mock(FuelSystem.class);
        landingGear = mock(LandingGear.class);
        navigationSystem = mock(NavigationSystem.class);

        aircraft = new Aircraft(engine, fuelSystem, landingGear, navigationSystem);
    }

    @Test
    @DisplayName("Constructor rejects null dependencies")
    void testConstructorNullDependencies() {
        assertThrows(NullPointerException.class, () -> new Aircraft(null, fuelSystem, landingGear, navigationSystem));
        assertThrows(NullPointerException.class, () -> new Aircraft(engine, null, landingGear, navigationSystem));
        assertThrows(NullPointerException.class, () -> new Aircraft(engine, fuelSystem, null, navigationSystem));
        assertThrows(NullPointerException.class, () -> new Aircraft(engine, fuelSystem, landingGear, null));
    }

    @Test
    @DisplayName("Complete successful flight cycle from grounded to landed")
    void testFullFlightCycleSuccess() {
        when(engine.isOperational()).thenReturn(true);
        when(fuelSystem.hasEnoughFuel()).thenReturn(true);
        when(navigationSystem.isRouteAvailable()).thenReturn(true);

        assertEquals(FlightStatus.GROUNDED, aircraft.getStatus());

        aircraft.prepareForTakeoff();
        assertEquals(FlightStatus.READY, aircraft.getStatus());

        aircraft.takeOff();
        assertEquals(FlightStatus.IN_FLIGHT, aircraft.getStatus());

        verify(engine, times(1)).start();
        verify(landingGear, times(1)).retract();

        aircraft.cruise(50.0);
        assertEquals(FlightStatus.IN_FLIGHT, aircraft.getStatus());
        verify(fuelSystem, times(1)).consumeFuel(50.0);

        aircraft.land();
        assertEquals(FlightStatus.LANDED, aircraft.getStatus());

        verify(landingGear, times(1)).deploy();
        verify(engine, times(1)).stop();
    }

    @Test
    @DisplayName("Aircraft can prepare and take off again after landing")
    void testPrepareForTakeoffAfterLanded() {
        when(engine.isOperational()).thenReturn(true);
        when(fuelSystem.hasEnoughFuel()).thenReturn(true);
        when(navigationSystem.isRouteAvailable()).thenReturn(true);

        aircraft.prepareForTakeoff();
        aircraft.takeOff();
        aircraft.land();
        assertEquals(FlightStatus.LANDED, aircraft.getStatus());

        aircraft.prepareForTakeoff();
        assertEquals(FlightStatus.READY, aircraft.getStatus());

        aircraft.takeOff();
        assertEquals(FlightStatus.IN_FLIGHT, aircraft.getStatus());
    }

    @Test
    @DisplayName("Takeoff aborts when engine fails to start")
    void testTakeOffAbortsOnEngineStartFailure() {
        when(engine.isOperational()).thenReturn(true);
        when(fuelSystem.hasEnoughFuel()).thenReturn(true);
        when(navigationSystem.isRouteAvailable()).thenReturn(true);

        aircraft.prepareForTakeoff();
        assertEquals(FlightStatus.READY, aircraft.getStatus());

        doThrow(new RuntimeException("Starter motor failure")).when(engine).start();

        FlightException exception = assertThrows(FlightException.class, () -> aircraft.takeOff());
        assertTrue(exception.getMessage().contains("Takeoff failed"));
        assertEquals(FlightStatus.ABORTED, aircraft.getStatus());

        verify(engine, times(1)).start();
        verify(landingGear, times(0)).retract();
    }

    @Test
    @DisplayName("Takeoff aborts when landing gear fails to retract")
    void testTakeOffAbortsOnLandingGearRetractFailure() {
        when(engine.isOperational()).thenReturn(true);
        when(fuelSystem.hasEnoughFuel()).thenReturn(true);
        when(navigationSystem.isRouteAvailable()).thenReturn(true);

        aircraft.prepareForTakeoff();
        assertEquals(FlightStatus.READY, aircraft.getStatus());

        doThrow(new RuntimeException("Hydraulic failure")).when(landingGear).retract();

        FlightException exception = assertThrows(FlightException.class, () -> aircraft.takeOff());
        assertTrue(exception.getMessage().contains("Takeoff failed"));
        assertEquals(FlightStatus.ABORTED, aircraft.getStatus());

        verify(engine, times(1)).start();
        verify(landingGear, times(1)).retract();
    }

    @Test
    @DisplayName("Preparation throws exception when sensor fails")
    void testPrepareForTakeoffFailsOnSensorException() {
        when(engine.isOperational()).thenReturn(true);
        when(fuelSystem.hasEnoughFuel()).thenThrow(new RuntimeException("Fuel sensor connection lost"));

        RuntimeException exception = assertThrows(RuntimeException.class, () -> aircraft.prepareForTakeoff());
        assertEquals("Fuel sensor connection lost", exception.getMessage());
        assertEquals(FlightStatus.GROUNDED, aircraft.getStatus());

        verify(navigationSystem, times(0)).isRouteAvailable();
    }

    @Test
    @DisplayName("Preparation throws exception when engine diagnostics fails")
    void testPrepareForTakeoffFailsOnEngineDiagnosticException() {
        when(engine.isOperational()).thenThrow(new RuntimeException("Engine diagnostic malfunction"));

        RuntimeException exception = assertThrows(RuntimeException.class, () -> aircraft.prepareForTakeoff());
        assertEquals("Engine diagnostic malfunction", exception.getMessage());
        assertEquals(FlightStatus.GROUNDED, aircraft.getStatus());

        verify(fuelSystem, times(0)).hasEnoughFuel();
        verify(navigationSystem, times(0)).isRouteAvailable();
    }

    @Test
    @DisplayName("Mid-flight engine failure triggers emergency landing without consuming fuel")
    void testCruiseEngineFailureTriggersEmergencyLanding() {
        when(engine.isOperational()).thenReturn(true);
        when(fuelSystem.hasEnoughFuel()).thenReturn(true);
        when(navigationSystem.isRouteAvailable()).thenReturn(true);

        aircraft.prepareForTakeoff();
        aircraft.takeOff();
        assertEquals(FlightStatus.IN_FLIGHT, aircraft.getStatus());

        when(engine.isOperational()).thenReturn(false);

        aircraft.cruise(25.0);

        assertEquals(FlightStatus.LANDED, aircraft.getStatus());

        verify(landingGear, times(1)).deploy();
        verify(engine, times(1)).stop();
        verify(fuelSystem, times(0)).consumeFuel(anyDouble());
    }

    @Test
    @DisplayName("Fuel consumption error during cruise triggers emergency landing and throws FlightException")
    void testCruiseFuelConsumptionErrorTriggersEmergencyLanding() {
        when(engine.isOperational()).thenReturn(true);
        when(fuelSystem.hasEnoughFuel()).thenReturn(true);
        when(navigationSystem.isRouteAvailable()).thenReturn(true);

        aircraft.prepareForTakeoff();
        aircraft.takeOff();
        assertEquals(FlightStatus.IN_FLIGHT, aircraft.getStatus());

        doThrow(new IllegalStateException("Fuel pump line pressure drop"))
                .when(fuelSystem).consumeFuel(anyDouble());

        FlightException exception = assertThrows(FlightException.class, () -> aircraft.cruise(40.0));
        assertTrue(exception.getMessage().contains("consuming fuel"));

        assertEquals(FlightStatus.LANDED, aircraft.getStatus());

        verify(landingGear, times(1)).deploy();
        verify(engine, times(1)).stop();
        verify(fuelSystem, times(1)).consumeFuel(40.0);
    }

    @Test
    @DisplayName("Direct emergency landing deploys gear and stops engine")
    void testDirectEmergencyLand() {
        when(engine.isOperational()).thenReturn(true);
        when(fuelSystem.hasEnoughFuel()).thenReturn(true);
        when(navigationSystem.isRouteAvailable()).thenReturn(true);

        aircraft.prepareForTakeoff();
        aircraft.takeOff();
        assertEquals(FlightStatus.IN_FLIGHT, aircraft.getStatus());

        aircraft.emergencyLand();
        assertEquals(FlightStatus.LANDED, aircraft.getStatus());

        verify(landingGear, times(1)).deploy();
        verify(engine, times(1)).stop();
    }

    @Test
    @DisplayName("Resetting mocks between flight phases to verify isolated interactions")
    void testMultiPhaseFlightWithMockReset() {
        when(engine.isOperational()).thenReturn(true);
        when(fuelSystem.hasEnoughFuel()).thenReturn(true);
        when(navigationSystem.isRouteAvailable()).thenReturn(true);

        aircraft.prepareForTakeoff();
        aircraft.takeOff();

        verify(engine, times(1)).start();
        verify(landingGear, times(1)).retract();

        reset(engine, landingGear);

        verify(engine, times(0)).start();
        verify(landingGear, times(0)).retract();

        aircraft.land();
        assertEquals(FlightStatus.LANDED, aircraft.getStatus());

        verify(landingGear, times(1)).deploy();
        verify(engine, times(1)).stop();
        verify(engine, times(0)).start();
    }

    @Test
    @DisplayName("Void method stubbing with doNothing and argument matching with any()")
    @SuppressWarnings("unchecked")
    void testDoNothingAndAnyArgumentMatcher() {
        doNothing().when(landingGear).deploy();
        doNothing().when(landingGear).retract();
        doNothing().when(engine).start();
        doNothing().when(engine).stop();
        doNothing().when(fuelSystem).consumeFuel(anyDouble());

        when(engine.isOperational()).thenReturn(true);
        when(fuelSystem.hasEnoughFuel()).thenReturn(true);
        when(navigationSystem.isRouteAvailable()).thenReturn(true);

        Consumer<FlightStatus> statusObserver = mock(Consumer.class);

        statusObserver.accept(aircraft.getStatus());

        aircraft.prepareForTakeoff();
        aircraft.takeOff();
        aircraft.cruise(15.0);
        aircraft.land();

        statusObserver.accept(aircraft.getStatus());

        verify(statusObserver, times(2)).accept(any());
        verify(statusObserver, times(2)).accept(any(FlightStatus.class));

        verify(fuelSystem, times(1)).consumeFuel(anyDouble());
        verify(landingGear, times(1)).deploy();
        verify(engine, times(1)).stop();
    }

    @Test
    @DisplayName("Preparation fails when fuel is insufficient or route unavailable")
    void testPrepareForTakeoffConditionsNotMet() {
        when(engine.isOperational()).thenReturn(false);
        FlightException exEngine = assertThrows(FlightException.class, () -> aircraft.prepareForTakeoff());
        assertTrue(exEngine.getMessage().contains("engine is not operational"));

        when(engine.isOperational()).thenReturn(true);
        when(fuelSystem.hasEnoughFuel()).thenReturn(false);
        FlightException exFuel = assertThrows(FlightException.class, () -> aircraft.prepareForTakeoff());
        assertTrue(exFuel.getMessage().contains("insufficient fuel"));

        when(fuelSystem.hasEnoughFuel()).thenReturn(true);
        when(navigationSystem.isRouteAvailable()).thenReturn(false);
        FlightException exRoute = assertThrows(FlightException.class, () -> aircraft.prepareForTakeoff());
        assertTrue(exRoute.getMessage().contains("route is unavailable"));

        assertEquals(FlightStatus.GROUNDED, aircraft.getStatus());
    }

    @Test
    @DisplayName("Invalid state transitions throw FlightException")
    void testInvalidLifecycleTransitions() {
        assertEquals(FlightStatus.GROUNDED, aircraft.getStatus());
        assertThrows(FlightException.class, () -> aircraft.takeOff());
        assertThrows(FlightException.class, () -> aircraft.cruise(10.0));
        assertThrows(FlightException.class, () -> aircraft.land());
        assertThrows(FlightException.class, () -> aircraft.emergencyLand());

        when(engine.isOperational()).thenReturn(true);
        when(fuelSystem.hasEnoughFuel()).thenReturn(true);
        when(navigationSystem.isRouteAvailable()).thenReturn(true);
        aircraft.prepareForTakeoff();
        aircraft.takeOff();
        assertEquals(FlightStatus.IN_FLIGHT, aircraft.getStatus());

        assertThrows(IllegalArgumentException.class, () -> aircraft.cruise(0.0));
        assertThrows(IllegalArgumentException.class, () -> aircraft.cruise(-15.0));
    }
}
