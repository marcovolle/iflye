package examples.algorithms;

import java.util.Set;

import algorithms.pm.VnePmMdvneAlgorithm;
import facade.ModelFacade;
import facade.config.ModelFacadeConfig;
import generators.OneTierNetworkGenerator;
import generators.TwoTierNetworkGenerator;
import generators.config.OneTierConfig;
import generators.config.TwoTierConfig;
import metrics.manager.GlobalMetricsManager;
import model.SubstrateNetwork;
import model.VirtualNetwork;

/**
 * Runnable example for the VNE pattern matching VNE algorithm implementation.
 *
 * @author Maximilian Kratz {@literal <maximilian.kratz@es.tu-darmstadt.de>}
 */
public class VnePmMdvneAlgorithmExampleSmall {

	/**
	 * Main method to start the example. String array of arguments will be ignored.
	 *
	 * @param args Will be ignored.
	 */
	public static void main(final String[] args) {
		// Setup
		ModelFacadeConfig.MIN_PATH_LENGTH = 1;
		ModelFacadeConfig.MAX_PATH_LENGTH = 4;

		GlobalMetricsManager.startRuntime();

		// Substrate network = two tier network
		final OneTierConfig rackConfig = new OneTierConfig(2, 1, false, 10, 10, 10, 10);
		final TwoTierConfig substrateConfig = new TwoTierConfig();
		substrateConfig.setRack(rackConfig);
		substrateConfig.setCoreBandwidth(100);
		substrateConfig.setNumberOfCoreSwitches(1);
		substrateConfig.setNumberOfRacks(2);
		final TwoTierNetworkGenerator subGen = new TwoTierNetworkGenerator(substrateConfig);
		subGen.createNetwork("sub", false);

		// Virtual network = one tier network
		final OneTierConfig virtualConfig = new OneTierConfig(2, 1, false, 10, 1, 1, 1);
		final OneTierNetworkGenerator virtGen = new OneTierNetworkGenerator(virtualConfig);
		virtGen.createNetwork("virt", true);

		final SubstrateNetwork sNet = (SubstrateNetwork) ModelFacade.getInstance().getNetworkById("sub");
		final VirtualNetwork vNet = (VirtualNetwork) ModelFacade.getInstance().getNetworkById("virt");

		// Create and execute algorithm
		final VnePmMdvneAlgorithm algo = VnePmMdvneAlgorithm.prepare(sNet, Set.of(vNet));
		algo.execute();

		GlobalMetricsManager.stopRuntime();

		// Save model to file
		ModelFacade.getInstance().persistModel();
		System.out.println("=> Execution finished.");

		// Time measurements
		System.out.println("=> Elapsed time (total): " + GlobalMetricsManager.getRuntime().getValue() / 1_000_000_000
				+ " seconds");
		System.out.println(
				"=> Elapsed time (PM): " + GlobalMetricsManager.getRuntime().getPmValue() / 1_000_000_000 + " seconds");
		System.out.println("=> Elapsed time (ILP): " + GlobalMetricsManager.getRuntime().getIlpValue() / 1_000_000_000
				+ " seconds");
		System.out.println("=> Elapsed time (rest): " + GlobalMetricsManager.getRuntime().getRestValue() / 1_000_000_000
				+ " seconds");

		System.exit(0);
	}

}
