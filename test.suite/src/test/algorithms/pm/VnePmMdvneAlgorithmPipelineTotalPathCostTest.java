package test.algorithms.pm;

import java.util.Set;
import algorithms.AlgorithmConfig;
import algorithms.AlgorithmConfig.Objective;
import algorithms.pm.VnePmMdvneAlgorithmPipelineTwoStagesVnet;
import model.SubstrateNetwork;
import model.VirtualNetwork;

/**
 * Test class for the VNE pattern matching algorithm implementation for minimizing the total path
 * cost metric including the pipeline functionality.
 * 
 * @author Maximilian Kratz {@literal <maximilian.kratz@stud.tu-darmstadt.de>}
 */
public class VnePmMdvneAlgorithmPipelineTotalPathCostTest
    extends VnePmMdvneAlgorithmTotalPathCostTest {

  @Override
  public void initAlgo(final SubstrateNetwork sNet, final Set<VirtualNetwork> vNets) {
    AlgorithmConfig.obj = Objective.TOTAL_PATH_COST;
    algo = VnePmMdvneAlgorithmPipelineTwoStagesVnet.prepare(sNet, vNets);
  }

}
