package examples.algorithms;

import algorithms.ilp.VneIlpPathAlgorithm;
import facade.ModelFacade;
import facade.config.ModelFacadeConfig;
import generators.OneTierNetworkGenerator;
import generators.TwoTierNetworkGenerator;
import generators.config.OneTierConfig;
import generators.config.TwoTierConfig;
import model.SubstrateNetwork;
import model.VirtualNetwork;

/**
 * Runnable example for the VNE ILP algorithm implementation.
 * 
 * @author Maximilian Kratz {@literal <maximilian.kratz@stud.tu-darmstadt.de>}
 */
public class VneIlpPathAlgorithmExample {

  /**
   * Main method to start the example. String array of arguments will be ignored.
   * 
   * @param args Will be ignored.
   */
  public static void main(final String[] args) {
    // Setup
    ModelFacadeConfig.MIN_PATH_LENGTH = 1;
    ModelFacadeConfig.MAX_PATH_LENGTH = 4;

    // Substrate network = one tier network
    final TwoTierConfig substrateConfig = new TwoTierConfig();
    substrateConfig.setCoreBandwidth(100);
    substrateConfig.setNumberOfCoreSwitches(1);
    substrateConfig.setNumberOfRacks(2);
    final TwoTierNetworkGenerator subGen = new TwoTierNetworkGenerator(substrateConfig);
    subGen.createNetwork("sub", false);

    // Virtual network = one tier network
    final OneTierConfig virtualConfig = new OneTierConfig(2, 1, false, 1, 1, 1, 1);
    final OneTierNetworkGenerator virtGen = new OneTierNetworkGenerator(virtualConfig);
    virtGen.createNetwork("virt", true);

    final SubstrateNetwork sNet =
        (SubstrateNetwork) ModelFacade.getInstance().getNetworkById("sub");
    final VirtualNetwork vNet = (VirtualNetwork) ModelFacade.getInstance().getNetworkById("virt");

    // Create and execute algorithm
    final VneIlpPathAlgorithm algo = new VneIlpPathAlgorithm(sNet, vNet);
    algo.execute();

    // Save model to file
    ModelFacade.getInstance().persistModel();
    System.out.println("=> Execution finished.");
  }

}
