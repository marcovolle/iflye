package test.algorithms.pm;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import algorithms.AbstractAlgorithm;
import algorithms.ilp.VneIlpPathAlgorithm;
import algorithms.pm.VnePmMdvneAlgorithm;
import facade.config.ModelFacadeConfig;
import generators.OneTierNetworkGenerator;
import generators.config.OneTierConfig;
import model.SubstrateNetwork;
import model.VirtualNetwork;
import test.algorithms.generic.AAlgorithmTest;

/**
 * Test class for the VNE pattern matching algorithm implementation for
 * repairing a removed virtual network in the model. This test should trigger
 * the algorithm to repair the substrate network information after a ungraceful
 * removal of a previously embedded virtual network.
 *
 * @author Maximilian Kratz {@literal <maximilian.kratz@stud.tu-darmstadt.de>}
 */
public class VnePmMdvneAlgorithmRepairModelNetworkTest extends AAlgorithmTest {

	@Override
	public void initAlgo(final SubstrateNetwork sNet, final Set<VirtualNetwork> vNets) {
		algo = VnePmMdvneAlgorithm.prepare(sNet, vNets);
	}

	@AfterEach
	public void resetAlgo() {
		if (algo != null) {
			((VnePmMdvneAlgorithm) algo).dispose();
		}
	}

	@Override
	@AfterEach
	public void restoreConfig() {
		// This method has to overwrite the method from super class, because we do not
		// want to validate
		// the model after each test. This is the case, because some tests intentionally
		// left the model
		// in an inconsistent state.
		// facade.validateModel();
		ModelFacadeConfig.MIN_PATH_LENGTH = oldLowerLimit;
		ModelFacadeConfig.MAX_PATH_LENGTH = oldUpperLimit;
		ModelFacadeConfig.IGNORE_BW = oldIgnoreBw;
	}

	/*
	 * Positive tests.
	 */
	@Test
	public void testConsistentModelAfterRepair() {
		// Setup
		setUpNetworks(4);
		final SubstrateNetwork sNet = (SubstrateNetwork) facade.getNetworkById("sub");
		final VirtualNetwork vNet = (VirtualNetwork) facade.getNetworkById("virt");

		// Embed first virtual network with another algorithm
		AbstractAlgorithm algoLoc = new VneIlpPathAlgorithm(sNet, Set.of(vNet));
		assertTrue(algoLoc.execute());

		// Remove the first virtual network ungracefully
		facade.removeNetworkFromRootSimple("virt");

		// Create another virtual network to embed
		final OneTierConfig virtConfig = new OneTierConfig(2, 1, false, 1, 1, 1, 1);
		final OneTierNetworkGenerator virtGen = new OneTierNetworkGenerator(virtConfig);
		virtGen.createNetwork("virt2", true);
		final VirtualNetwork vNet2 = (VirtualNetwork) facade.getNetworkById("virt2");

		// Embed the second virtual network
		initAlgo(sNet, Set.of(vNet2));
		assertTrue(algo.execute());
		assertNotNull(((VirtualNetwork) facade.getNetworkById("virt2")).getHost());

		// Validation must pass to show that the algorithm repaired the model of the
		// substrate network
		facade.validateModel();
	}

	/*
	 * Negative tests.
	 */
	@Test
	public void testUnconsistentModelBefore() {
		setUpNetworks(4);
		final SubstrateNetwork sNet = (SubstrateNetwork) facade.getNetworkById("sub");
		final VirtualNetwork vNet = (VirtualNetwork) facade.getNetworkById("virt");

		// Usage of other algorithm on purpose
		final VneIlpPathAlgorithm algo = new VneIlpPathAlgorithm(sNet, Set.of(vNet));
		assertTrue(algo.execute());

		facade.removeNetworkFromRootSimple("virt");
		assertThrows(InternalError.class, () -> {
			facade.validateModel();
		});
	}

	/*
	 * Utility methods
	 */

	/**
	 * Static setup method for creating the necessary networks for this tests.
	 *
	 * @param substrateServers Number of substrate servers to build
	 */
	protected static void setUpNetworks(final int substrateServers) {
		final OneTierConfig subConfig = new OneTierConfig(substrateServers, 1, false, 1, 1, 1, 10);
		final OneTierNetworkGenerator subGen = new OneTierNetworkGenerator(subConfig);
		subGen.createNetwork("sub", false);

		final OneTierConfig virtConfig = new OneTierConfig(2, 1, false, 1, 1, 1, 1);
		final OneTierNetworkGenerator virtGen = new OneTierNetworkGenerator(virtConfig);
		virtGen.createNetwork("virt", true);
	}

}
