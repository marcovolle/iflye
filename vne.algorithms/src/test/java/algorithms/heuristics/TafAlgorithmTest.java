package algorithms.heuristics;

import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import facade.ModelFacade;
import facade.config.ModelFacadeConfig;
import model.SubstrateNetwork;
import model.VirtualNetwork;

/**
 * Test class for the TAF algorithm implementation.
 * 
 * @author Maximilian Kratz <maximilian.kratz@stud.tu-darmstadt.de>
 */
public class TafAlgorithmTest {

  @BeforeEach
  public void resetModel() {
    ModelFacade.getInstance().resetAll();

    // Network setup
    ModelFacade.getInstance().addNetworkToRoot("sub", false);
    ModelFacade.getInstance().addNetworkToRoot("virt", true);

    // Normal model setup
    ModelFacadeConfig.MIN_PATH_LENGTH = 1;
    ModelFacadeConfig.IGNORE_BW = true;
  }

  @Test
  public void testRejectIgnoreBandwidth() {
    ModelFacadeConfig.IGNORE_BW = false;

    final SubstrateNetwork sNet =
        (SubstrateNetwork) ModelFacade.getInstance().getNetworkById("sub");
    final VirtualNetwork vNet = (VirtualNetwork) ModelFacade.getInstance().getNetworkById("virt");

    assertThrows(UnsupportedOperationException.class, () -> {
      new TafAlgorithm(sNet, vNet);
    });
  }

  @Test
  public void testRejectSubstrateServerConnections() {
    ModelFacade.getInstance().addSwitchToNetwork("sw", "sub", 0);
    ModelFacade.getInstance().addServerToNetwork("srv1", "sub", 1, 1, 1, 1);
    ModelFacade.getInstance().addServerToNetwork("srv2", "sub", 1, 1, 1, 1);
    ModelFacade.getInstance().addLinkToNetwork("ln1", "sub", 1, "srv1", "sw");
    ModelFacade.getInstance().addLinkToNetwork("ln2", "sub", 1, "srv1", "srv2");

    final SubstrateNetwork sNet =
        (SubstrateNetwork) ModelFacade.getInstance().getNetworkById("sub");
    final VirtualNetwork vNet = (VirtualNetwork) ModelFacade.getInstance().getNetworkById("virt");

    assertThrows(UnsupportedOperationException.class, () -> {
      new TafAlgorithm(sNet, vNet);
    });
  }

  @Test
  public void testRejectOneVirtualServer() {
    oneTierSetupTwoServers("sub", 1);

    ModelFacade.getInstance().addSwitchToNetwork("sw", "virt", 0);
    ModelFacade.getInstance().addServerToNetwork("srv1", "virt", 1, 1, 1, 1);
    ModelFacade.getInstance().addLinkToNetwork("ln1", "virt", 1, "srv1", "sw");
    ModelFacade.getInstance().addLinkToNetwork("ln2", "virt", 1, "sw", "srv1");

    final SubstrateNetwork sNet =
        (SubstrateNetwork) ModelFacade.getInstance().getNetworkById("sub");
    final VirtualNetwork vNet = (VirtualNetwork) ModelFacade.getInstance().getNetworkById("virt");

    assertThrows(UnsupportedOperationException.class, () -> {
      new TafAlgorithm(sNet, vNet);
    });
  }

  @Test
  public void testRejectMinPathLength() {
    ModelFacadeConfig.MIN_PATH_LENGTH = 3;

    final SubstrateNetwork sNet =
        (SubstrateNetwork) ModelFacade.getInstance().getNetworkById("sub");
    final VirtualNetwork vNet = (VirtualNetwork) ModelFacade.getInstance().getNetworkById("virt");

    assertThrows(UnsupportedOperationException.class, () -> {
      new TafAlgorithm(sNet, vNet);
    });
  }

  /*
   * Utility methods.
   */

  /**
   * Creates a one tier network with two servers and one switch.
   */
  private static void oneTierSetupTwoServers(final String id, final int slotPerServer) {
    ModelFacade.getInstance().addSwitchToNetwork("sw", id, 0);
    ModelFacade.getInstance().addServerToNetwork("srv1", id, slotPerServer, slotPerServer,
        slotPerServer, 1);
    ModelFacade.getInstance().addServerToNetwork("srv2", id, slotPerServer, slotPerServer,
        slotPerServer, 1);
    ModelFacade.getInstance().addLinkToNetwork("ln1", id, 1, "srv1", "sw");
    ModelFacade.getInstance().addLinkToNetwork("ln2", id, 1, "srv2", "sw");
    ModelFacade.getInstance().addLinkToNetwork("ln3", id, 1, "sw", "srv1");
    ModelFacade.getInstance().addLinkToNetwork("ln4", id, 1, "sw", "srv2");
  }


}
