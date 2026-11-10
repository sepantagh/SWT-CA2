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

/**
 * Unit tests for {@link Aircraft} using Mockito and JUnit 5.
 *
 * <p>These tests are designed based on the principles discussed in Martin Fowler's
 * article "Mocks Aren't Stubs" (https://martinfowler.com/articles/mocksArentStubs.html).
 * They demonstrate both:
 * <ul>
 *   <li><b>State Verification (Stubbing):</b> Configuring test doubles to provide canned
 *       responses (via {@code when(...).thenReturn(...)}), and asserting on the resultant
 *       state of the System Under Test (SUT).</li>
 *   <li><b>Behavior Verification (Mocking):</b> Verifying that the SUT correctly interacts
 *       with its collaborators by calling expected methods with expected arguments
 *       (via {@code verify(...)}) and frequencies (via {@code times(...)}).</li>
 * </ul>
 *
 * <p>Required Mockito features demonstrated across this test suite:
 * {@code mock()}, {@code reset()}, {@code when()}, {@code thenReturn()},
 * {@code verify()}, {@code times()}, {@code any()}, {@code thenThrow()}, and {@code doNothing()}.
 */
class AircraftTest {

    private Engine engine;
    private FuelSystem fuelSystem;
    private LandingGear landingGear;
    private NavigationSystem navigationSystem;
    private Aircraft aircraft;

    @BeforeEach
    void setUp() {
        // mock(): Creating test doubles for all collaborators of Aircraft
        engine = mock(Engine.class);
        fuelSystem = mock(FuelSystem.class);
        landingGear = mock(LandingGear.class);
        navigationSystem = mock(NavigationSystem.class);

        aircraft = new Aircraft(engine, fuelSystem, landingGear, navigationSystem);
    }

    /**
     * Test 1: Full successful flight lifecycle.
     * Demonstrates: mock(), when(), thenReturn(), verify(), times()
     *
     * <p>Combines State Verification (checking aircraft status after each phase)
     * and Behavior Verification (verifying collaborators were called as expected).
     */
    @Test
    @DisplayName("Complete successful flight cycle from grounded to landed")
    void testFullFlightCycleSuccess() {
        // --- STUBBING (State preparation for collaborators) ---
        when(engine.isOperational()).thenReturn(true);
        when(fuelSystem.hasEnoughFuel()).thenReturn(true);
        when(navigationSystem.isRouteAvailable()).thenReturn(true);

        // Initial state verification
        assertEquals(FlightStatus.GROUNDED, aircraft.getStatus());

        // 1. Prepare for takeoff
        aircraft.prepareForTakeoff();
        assertEquals(FlightStatus.READY, aircraft.getStatus());

        // 2. Takeoff
        aircraft.takeOff();
        assertEquals(FlightStatus.IN_FLIGHT, aircraft.getStatus());

        // Behavior verification for takeoff:
        // Engine must be started and landing gear retracted exactly once.
        verify(engine, times(1)).start();
        verify(landingGear, times(1)).retract();

        // 3. Cruise
        aircraft.cruise(50.0);
        assertEquals(FlightStatus.IN_FLIGHT, aircraft.getStatus());
        verify(fuelSystem, times(1)).consumeFuel(50.0);

        // 4. Land
        aircraft.land();
        assertEquals(FlightStatus.LANDED, aircraft.getStatus());

        // Behavior verification for landing:
        // Landing gear deployed and engine stopped.
        verify(landingGear, times(1)).deploy();
        verify(engine, times(1)).stop();
    }

    /**
     * Test 2: Takeoff abort on engine start failure.
     * Demonstrates: doThrow(), verify(), times()
     *
     * <p>Behavior verification: When engine start throws a RuntimeException during takeoff,
     * the aircraft status must become ABORTED, FlightException must be thrown,
     * and landing gear retract must NOT be called.
     */
    @Test
    @DisplayName("Takeoff aborts when engine fails to start")
    void testTakeOffAbortsOnEngineStartFailure() {
        // Stubs for preparation
        when(engine.isOperational()).thenReturn(true);
        when(fuelSystem.hasEnoughFuel()).thenReturn(true);
        when(navigationSystem.isRouteAvailable()).thenReturn(true);

        aircraft.prepareForTakeoff();
        assertEquals(FlightStatus.READY, aircraft.getStatus());

        // Simulate engine hardware failure during startup
        doThrow(new RuntimeException("Starter motor failure")).when(engine).start();

        FlightException exception = assertThrows(FlightException.class, () -> aircraft.takeOff());
        assertTrue(exception.getMessage().contains("Takeoff failed"));
        assertEquals(FlightStatus.ABORTED, aircraft.getStatus());

        // Behavior verification:
        // engine.start() was attempted once
        verify(engine, times(1)).start();
        // landingGear.retract() should never be called because start failed first
        verify(landingGear, times(0)).retract();
    }

    /**
     * Test 3: Preparation failure due to sensor error.
     * Demonstrates: when(), thenThrow(), verify(), times()
     *
     * <p>Fowler's Stubbing concept: The stubbed fuel system throws an exception
     * representing a hardware sensor malfunction. We verify that preparation halts
     * and subsequent checks (route availability) are never called (short-circuiting).
     */
    @Test
    @DisplayName("Preparation throws exception when sensor fails")
    void testPrepareForTakeoffFailsOnSensorException() {
        when(engine.isOperational()).thenReturn(true);
        // thenThrow(): simulate sensor hardware malfunction
        when(fuelSystem.hasEnoughFuel()).thenThrow(new RuntimeException("Fuel sensor connection lost"));

        RuntimeException exception = assertThrows(RuntimeException.class, () -> aircraft.prepareForTakeoff());
        assertEquals("Fuel sensor connection lost", exception.getMessage());

        // State verification: Aircraft remains grounded
        assertEquals(FlightStatus.GROUNDED, aircraft.getStatus());

        // Behavior verification: Navigation route was never queried due to short-circuit
        verify(navigationSystem, times(0)).isRouteAvailable();
    }

    /**
     * Test 4: Emergency landing during cruise when engine becomes inoperable.
     * Demonstrates: when(), thenReturn(), verify(), times(), anyDouble()
     *
     * <p>Tests simulated mid-flight engine failure: cruise() detects non-operational engine,
     * performs an emergency landing, and does NOT attempt to consume fuel.
     */
    @Test
    @DisplayName("Mid-flight engine failure triggers emergency landing without consuming fuel")
    void testCruiseEngineFailureTriggersEmergencyLanding() {
        // Prepare and take off
        when(engine.isOperational()).thenReturn(true);
        when(fuelSystem.hasEnoughFuel()).thenReturn(true);
        when(navigationSystem.isRouteAvailable()).thenReturn(true);

        aircraft.prepareForTakeoff();
        aircraft.takeOff();
        assertEquals(FlightStatus.IN_FLIGHT, aircraft.getStatus());

        // Mid-flight: engine fails (isOperational returns false)
        when(engine.isOperational()).thenReturn(false);

        aircraft.cruise(25.0);

        // State verification: aircraft made emergency landing
        assertEquals(FlightStatus.LANDED, aircraft.getStatus());

        // Behavior verification:
        // Landing gear deployed and engine stopped for emergency landing
        verify(landingGear, times(1)).deploy();
        verify(engine, times(1)).stop();
        // Fuel consumption was not attempted because engine was not operational
        verify(fuelSystem, times(0)).consumeFuel(anyDouble());
    }

    /**
     * Test 5: Fuel system failure during cruise triggers emergency landing and throws FlightException.
     * Demonstrates: doThrow(), verify(), times(), anyDouble()
     */
    @Test
    @DisplayName("Fuel consumption error during cruise triggers emergency landing and throws FlightException")
    void testCruiseFuelConsumptionErrorTriggersEmergencyLanding() {
        // Setup flight to IN_FLIGHT
        when(engine.isOperational()).thenReturn(true);
        when(fuelSystem.hasEnoughFuel()).thenReturn(true);
        when(navigationSystem.isRouteAvailable()).thenReturn(true);

        aircraft.prepareForTakeoff();
        aircraft.takeOff();
        assertEquals(FlightStatus.IN_FLIGHT, aircraft.getStatus());

        // Fuel system throws during consumption (e.g. pump failure or lack of fuel)
        doThrow(new IllegalStateException("Fuel pump line pressure drop"))
                .when(fuelSystem).consumeFuel(anyDouble());

        FlightException exception = assertThrows(FlightException.class, () -> aircraft.cruise(40.0));
        assertTrue(exception.getMessage().contains("consuming fuel"));

        // State verification: aircraft landed due to emergency landing
        assertEquals(FlightStatus.LANDED, aircraft.getStatus());

        // Behavior verification: emergency landing deployed gear and stopped engine
        verify(landingGear, times(1)).deploy();
        verify(engine, times(1)).stop();
        verify(fuelSystem, times(1)).consumeFuel(40.0);
    }

    /**
     * Test 6: Multi-stage flight lifecycle demonstrating mock reset.
     * Demonstrates: reset(), mock(), when(), thenReturn(), verify(), times()
     *
     * <p>Fowler's discussion on mock state: When reusing mocks across multiple sequential
     * scenarios or test phases, {@code reset(mock)} clears all prior recorded interactions
     * and stubs, allowing clean verification of the subsequent phase.
     */
    @Test
    @DisplayName("Resetting mocks between flight phases to verify isolated interactions")
    void testMultiPhaseFlightWithMockReset() {
        // Phase 1: Takeoff
        when(engine.isOperational()).thenReturn(true);
        when(fuelSystem.hasEnoughFuel()).thenReturn(true);
        when(navigationSystem.isRouteAvailable()).thenReturn(true);

        aircraft.prepareForTakeoff();
        aircraft.takeOff();

        verify(engine, times(1)).start();
        verify(landingGear, times(1)).retract();

        // reset(): Clear all recorded interactions on engine and landingGear
        reset(engine, landingGear);

        // Before landing, verify that past interactions are wiped out by reset
        verify(engine, times(0)).start();
        verify(landingGear, times(0)).retract();

        // Phase 2: Landing
        aircraft.land();
        assertEquals(FlightStatus.LANDED, aircraft.getStatus());

        // Verify only the interactions that occurred AFTER reset
        verify(landingGear, times(1)).deploy();
        verify(engine, times(1)).stop();
        // start() was not called in Phase 2
        verify(engine, times(0)).start();
    }

    /**
     * Test 7: Explicit void method stubbing and argument matchers.
     * Demonstrates: doNothing(), any(), anyDouble(), verify(), times()
     *
     * <p>Demonstrates {@code doNothing()} on void collaborator methods, and uses {@code any()}
     * with an observer/consumer mock to verify event/state notifications.
     */
    @Test
    @DisplayName("Void method stubbing with doNothing and argument matching with any()")
    @SuppressWarnings("unchecked")
    void testDoNothingAndAnyArgumentMatcher() {
        // doNothing(): Explicitly configuring void methods
        doNothing().when(landingGear).deploy();
        doNothing().when(landingGear).retract();
        doNothing().when(engine).start();
        doNothing().when(engine).stop();
        doNothing().when(fuelSystem).consumeFuel(anyDouble());

        when(engine.isOperational()).thenReturn(true);
        when(fuelSystem.hasEnoughFuel()).thenReturn(true);
        when(navigationSystem.isRouteAvailable()).thenReturn(true);

        // Mock an external flight status observer to demonstrate any() argument matcher on Object
        Consumer<FlightStatus> statusObserver = mock(Consumer.class);

        // Notify initial status
        statusObserver.accept(aircraft.getStatus());

        aircraft.prepareForTakeoff();
        aircraft.takeOff();
        aircraft.cruise(15.0);
        aircraft.land();

        // Notify final status
        statusObserver.accept(aircraft.getStatus());

        // Behavior verification using any():
        // Verify that the observer received notifications matching any FlightStatus
        verify(statusObserver, times(2)).accept(any());
        verify(statusObserver, times(2)).accept(any(FlightStatus.class));

        // Verify collaborator interactions
        verify(fuelSystem, times(1)).consumeFuel(anyDouble());
        verify(landingGear, times(1)).deploy();
        verify(engine, times(1)).stop();
    }

    /**
     * Test 8: Preparation failure when conditions are not met (State verification with stubs).
     * Demonstrates: when(), thenReturn(), verify(), times()
     */
    @Test
    @DisplayName("Preparation fails when fuel is insufficient or route unavailable")
    void testPrepareForTakeoffConditionsNotMet() {
        // Case A: Engine not operational
        when(engine.isOperational()).thenReturn(false);
        FlightException exEngine = assertThrows(FlightException.class, () -> aircraft.prepareForTakeoff());
        assertTrue(exEngine.getMessage().contains("engine is not operational"));

        // Case B: Engine ok, but fuel insufficient
        when(engine.isOperational()).thenReturn(true);
        when(fuelSystem.hasEnoughFuel()).thenReturn(false);
        FlightException exFuel = assertThrows(FlightException.class, () -> aircraft.prepareForTakeoff());
        assertTrue(exFuel.getMessage().contains("insufficient fuel"));

        // Case C: Engine ok, fuel ok, but route unavailable
        when(fuelSystem.hasEnoughFuel()).thenReturn(true);
        when(navigationSystem.isRouteAvailable()).thenReturn(false);
        FlightException exRoute = assertThrows(FlightException.class, () -> aircraft.prepareForTakeoff());
        assertTrue(exRoute.getMessage().contains("route is unavailable"));

        assertEquals(FlightStatus.GROUNDED, aircraft.getStatus());
    }

    /**
     * Test 9: Lifecycle restrictions and invalid state transitions.
     * Verifies that operations are rejected when called in invalid states.
     */
    @Test
    @DisplayName("Invalid state transitions throw FlightException")
    void testInvalidLifecycleTransitions() {
        // Cannot take off while GROUNDED
        assertEquals(FlightStatus.GROUNDED, aircraft.getStatus());
        assertThrows(FlightException.class, () -> aircraft.takeOff());

        // Cannot cruise while GROUNDED
        assertThrows(FlightException.class, () -> aircraft.cruise(10.0));

        // Cannot land while GROUNDED
        assertThrows(FlightException.class, () -> aircraft.land());

        // Cannot emergency land while GROUNDED
        assertThrows(FlightException.class, () -> aircraft.emergencyLand());

        // Invalid cruise amount
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
