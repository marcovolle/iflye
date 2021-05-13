package algorithms.ilp;

import algorithms.generic.AAlgorithmTwoTierTest;
import model.SubstrateNetwork;
import model.VirtualNetwork;

/**
 * Test class for the VNE ILP algorithm implementation.
 * 
 * @author Maximilian Kratz {@literal <maximilian.kratz@stud.tu-darmstadt.de>}
 */
public class VneIlpPathAlgorithmTest extends AAlgorithmTwoTierTest {

  @Override
  public void initAlgo(final SubstrateNetwork sNet, final VirtualNetwork vNet) {
    algo = new VneIlpPathAlgorithm(sNet, vNet);
  }

}
