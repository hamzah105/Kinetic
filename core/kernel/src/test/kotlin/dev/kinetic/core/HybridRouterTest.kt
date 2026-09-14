package dev.kinetic.core

import dev.kinetic.core.model.*
import kotlin.test.*

class HybridRouterTest {
    private val router = HybridModelRouter()
    private val local = RouteCandidate(RouteProvider.LOCAL_LLAMA, false, RouteAvailability.UNKNOWN, false, 1024)
    private val cloud = RouteCandidate(RouteProvider.CLOUD, true, RouteAvailability.UNKNOWN, true, 8192)
    private fun decide(requirements: RoutingRequirements = RoutingRequirements(),
        environment: RoutingEnvironment = RoutingEnvironment(NetworkState.AVAILABLE),
        candidates: List<RouteCandidate> = listOf(cloud, local)) = router.decide(RoutingMode.HYBRID, requirements, environment, candidates)

    @Test fun `manual pin honored without selection fallback`() {
        val result = router.decide(RoutingMode.MANUAL, RoutingRequirements(explicitPin = RouteProvider.CLOUD),
            RoutingEnvironment(), listOf(local, cloud))
        assertEquals(RouteProvider.CLOUD, result.selected)
        assertEquals(listOf(RouteReason.NOT_PINNED_PROVIDER), result.candidates.first().rejections)
    }
    @Test fun `private explicit cloud pin is rejected instead of changed`() {
        val result = decide(RoutingRequirements(localOnly = true, explicitPin = RouteProvider.CLOUD))
        assertNull(result.selected); assertTrue(result.cloudProhibited)
        assertEquals(RouteReason.NO_ELIGIBLE_PROVIDER, result.reason)
    }
    @Test fun `privacy forbids cloud even when local unavailable`() {
        assertNull(decide(RoutingRequirements(localOnly = true), candidates = listOf(cloud,
            local.copy(availability = RouteAvailability.UNAVAILABLE))).selected)
    }
    @Test fun `offline and unknown network both exclude hybrid cloud`() {
        for (network in listOf(NetworkState.UNKNOWN, NetworkState.UNAVAILABLE)) {
            assertNull(decide(environment = RoutingEnvironment(network), candidates = listOf(cloud)).selected)
        }
    }
    @Test fun `missing model excluded and recorded`() {
        val result = decide(candidates = listOf(local.copy(availability = RouteAvailability.UNAVAILABLE)))
        assertNull(result.selected); assertTrue(result.localUnavailable)
    }
    @Test fun `context over 1024 excludes local`() {
        assertEquals(RouteProvider.CLOUD, decide(RoutingRequirements(minimumContextTokens = 1025)).selected)
        assertEquals(RouteProvider.LOCAL_LLAMA, decide(RoutingRequirements(minimumContextTokens = 1024)).selected)
    }
    @Test fun `tool requirement excludes text only local`() {
        val result = decide(RoutingRequirements(structuredToolsRequired = true))
        assertEquals(RouteProvider.CLOUD, result.selected)
        assertContains(result.candidates.first().rejections, RouteReason.TOOLS_UNSUPPORTED)
    }
    @Test fun `unknown telemetry remains unknown not measured zero`() {
        val result = decide(environment = RoutingEnvironment())
        assertEquals(ResourceState.UNKNOWN, result.environment.battery)
        assertEquals(ResourceState.UNKNOWN, result.environment.thermal)
        assertEquals(RouteAvailability.UNKNOWN, result.candidates.first().candidate.availability)
        assertEquals(RouteProvider.LOCAL_LLAMA, result.selected) // Attempt only, not readiness certification.
        val unknownCapacity = decide(candidates = listOf(cloud.copy(maxContextTokens = null)))
        assertNull(unknownCapacity.candidates.single().candidate.maxContextTokens)
    }
    @Test fun `known constrained resources exclude local`() {
        assertEquals(RouteProvider.CLOUD, decide(environment = RoutingEnvironment(NetworkState.AVAILABLE,
            thermal = ResourceState.CONSTRAINED)).selected)
    }
    @Test fun `tie breaking is deterministic and local first regardless of enumeration`() {
        assertEquals(decide(candidates = listOf(local, cloud)), decide(candidates = listOf(cloud, local)))
        assertEquals(RouteProvider.LOCAL_LLAMA, decide().selected)
    }
    @Test fun `hybrid excludes fake and simulated unless explicitly pinned`() {
        val fake = RouteCandidate(RouteProvider.FAKE, false, RouteAvailability.AVAILABLE, true, null, true)
        assertNull(decide(candidates = listOf(fake)).selected)
        assertEquals(RouteProvider.FAKE, decide(RoutingRequirements(explicitPin = RouteProvider.FAKE), candidates = listOf(fake)).selected)
    }
}
