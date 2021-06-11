package test.algorithms.pm;

import java.util.Set;
import algorithms.pm.VnePmMdvneAlgorithmUpdate;
import model.SubstrateNetwork;
import model.VirtualNetwork;

/**
 * Test class for the VNE pattern matching algorithm update implementation for repairing a removed
 * virtual network in the model. This test should trigger the algorithm to repair the substrate
 * network information after a ungraceful removal of a previously embedded virtual network.
 * 
 * @author Maximilian Kratz {@literal <maximilian.kratz@stud.tu-darmstadt.de>}
 */
public class VnePmMdvneAlgorithmUpdateRepairModelNetworkTest
    extends VnePmMdvneAlgorithmRepairModelNetworkTest {

  @Override
  public void initAlgo(final SubstrateNetwork sNet, final Set<VirtualNetwork> vNets) {
    algo = VnePmMdvneAlgorithmUpdate.prepare(sNet, vNets);
  }

}
