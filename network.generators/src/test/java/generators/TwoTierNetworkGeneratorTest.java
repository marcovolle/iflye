package generators;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import java.util.List;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import facade.ModelFacade;
import generators.config.OneTierConfig;
import generators.config.TwoTierConfig;
import model.Link;
import model.Network;
import model.Node;
import model.Path;
import model.Server;
import model.SubstrateLink;
import model.SubstrateNetwork;
import model.SubstrateServer;
import model.Switch;
import model.VirtualNetwork;

/**
 * Test class for the TwoTierNetworkGenerator.
 * 
 * @author Maximilian Kratz <maximilian.kratz@stud.tu-darmstadt.de>
 */
public class TwoTierNetworkGeneratorTest {

  @Before
  public void resetModel() {
    ModelFacade.getInstance().resetAll();
  }

  /*
   * Positive tests
   */

  @Test
  public void testNoNetworksAfterInit() {
    final TwoTierConfig config = new TwoTierConfig();
    new TwoTierNetworkGenerator(config);
    assertTrue(ModelFacade.getInstance().getAllNetworks().isEmpty());
  }

  @Test
  public void testNumberOfElementsSmallSubstrate() {
    final OneTierConfig rackConfig = new OneTierConfig(2, 1, false, 0, 0, 0, 0);
    final TwoTierConfig config = new TwoTierConfig();
    config.setNumberOfCoreSwitches(2);
    config.setNumberOfRacks(2);
    config.setRack(rackConfig);

    final TwoTierNetworkGenerator gen = new TwoTierNetworkGenerator(config);
    gen.createNetwork("test", false);

    final Network net = ModelFacade.getInstance().getNetworkById("test");

    // Number of nodes
    assertEquals(8, net.getNodes().size());

    // Servers
    assertEquals(4, ModelFacade.getInstance().getAllServersOfNetwork("test").size());

    // Switches
    assertEquals(4, ModelFacade.getInstance().getAllSwitchesOfNetwork("test").size());

    // Links
    assertEquals(16, net.getLinks().size());

    // Paths:
    // 4 servers bidirectional to 3 other servers;
    // 4 servers bidirectional to 2 core switches;
    // 4 servers bidirectional to one rack-switch each;
    assertEquals((4 * (4 - 1) + 4 * 2 * 2 + 2 * 4 * 1), net.getPaths().size());
  }

  @Test
  public void testNumberOfElementsSmallVirtual() {
    final OneTierConfig rackConfig = new OneTierConfig(2, 1, false, 0, 0, 0, 0);
    final TwoTierConfig config = new TwoTierConfig();
    config.setNumberOfCoreSwitches(2);
    config.setNumberOfRacks(2);
    config.setRack(rackConfig);

    final TwoTierNetworkGenerator gen = new TwoTierNetworkGenerator(config);
    gen.createNetwork("test", true);

    final Network net = ModelFacade.getInstance().getNetworkById("test");

    // Number of nodes
    assertEquals(8, net.getNodes().size());

    // Servers
    assertEquals(4, ModelFacade.getInstance().getAllServersOfNetwork("test").size());

    // Switches
    assertEquals(4, ModelFacade.getInstance().getAllSwitchesOfNetwork("test").size());

    // Links
    assertEquals(16, net.getLinks().size());

    // Paths
    assertEquals(0, net.getPaths().size());
  }

  @Test
  public void testNumberOfElementsLarge() {
    final OneTierConfig rackConfig = new OneTierConfig(100, 1, false, 0, 0, 0, 0);
    final TwoTierConfig config = new TwoTierConfig();
    config.setNumberOfCoreSwitches(10);
    config.setNumberOfRacks(2);
    config.setRack(rackConfig);
    final TwoTierNetworkGenerator gen = new TwoTierNetworkGenerator(config);
    gen.createNetwork("test", false);

    final Network net = ModelFacade.getInstance().getNetworkById("test");

    // Number of nodes
    assertEquals(212, net.getNodes().size());

    // Servers
    assertEquals(200, ModelFacade.getInstance().getAllServersOfNetwork("test").size());

    // Switches
    assertEquals(12, ModelFacade.getInstance().getAllSwitchesOfNetwork("test").size());

    // Links
    assertEquals(440, net.getLinks().size());

    // Paths:
    // 200 servers bidirectional to 199 other servers;
    // 200 servers bidirectional to 10 core switches;
    // 200 servers bidirectional to one rack-switch each;
    assertEquals((200 * (200 - 1) + 200 * 10 * 2 + 2 * 200 * 1), net.getPaths().size());
  }

  @Test
  public void testVirtualSubstrate() {
    final TwoTierConfig config = new TwoTierConfig();
    final TwoTierNetworkGenerator gen = new TwoTierNetworkGenerator(config);
    gen.createNetwork("sub", false);
    gen.createNetwork("virt", true);

    assertEquals(2, ModelFacade.getInstance().getAllNetworks().size());
    assertTrue(ModelFacade.getInstance().getNetworkById("sub") instanceof SubstrateNetwork);
    assertTrue(ModelFacade.getInstance().getNetworkById("virt") instanceof VirtualNetwork);
  }

  @Test
  public void testServerNormalParameters() {
    final OneTierConfig rackConfig = new OneTierConfig(2, 1, false, 1, 2, 3, 0);
    final TwoTierConfig config = new TwoTierConfig();
    config.setRack(rackConfig);
    final TwoTierNetworkGenerator gen = new TwoTierNetworkGenerator(config);
    gen.createNetwork("sub", false);

    for (final Node n : ModelFacade.getInstance().getAllServersOfNetwork("sub")) {
      final SubstrateServer srv = (SubstrateServer) n;
      assertEquals(1, srv.getCpu());
      assertEquals(2, srv.getMemory());
      assertEquals(3, srv.getStorage());
    }
  }

  @Test
  public void testServerResidualParameters() {
    final OneTierConfig rackConfig = new OneTierConfig(2, 1, false, 1, 2, 3, 0);
    final TwoTierConfig config = new TwoTierConfig();
    config.setRack(rackConfig);
    final TwoTierNetworkGenerator gen = new TwoTierNetworkGenerator(config);
    gen.createNetwork("sub", false);

    for (final Node n : ModelFacade.getInstance().getAllServersOfNetwork("sub")) {
      final SubstrateServer srv = (SubstrateServer) n;
      assertEquals(srv.getCpu(), srv.getResidualCpu());
      assertEquals(srv.getMemory(), srv.getResidualMemory());
      assertEquals(srv.getStorage(), srv.getResidualStorage());
    }
  }

  @Test
  public void testServerTwoLinksEach() {
    final TwoTierConfig config = new TwoTierConfig();
    final TwoTierNetworkGenerator gen = new TwoTierNetworkGenerator(config);
    gen.createNetwork("sub", false);

    for (final Node n : ModelFacade.getInstance().getAllServersOfNetwork("sub")) {
      final SubstrateServer srv = (SubstrateServer) n;
      assertEquals(1, srv.getOutgoingLinks().size());
      assertEquals(1, srv.getIncomingLinks().size());
    }
  }

  @Test
  public void testLinkParameters() {
    final OneTierConfig rackConfig = new OneTierConfig(2, 1, false, 0, 0, 0, 7);
    final TwoTierConfig config = new TwoTierConfig();
    config.setRack(rackConfig);
    config.setCoreBandwidth(8);
    final TwoTierNetworkGenerator gen = new TwoTierNetworkGenerator(config);
    gen.createNetwork("sub", false);

    final List<Link> links = ModelFacade.getInstance().getAllLinksOfNetwork("sub");

    for (final Link l : links) {
      final SubstrateLink sl = (SubstrateLink) l;
      assertNotNull(l.getSource());
      assertNotNull(l.getTarget());
      assertEquals(l.getBandwidth(), sl.getResidualBandwidth());

      if (l.getSource() instanceof Server || l.getTarget() instanceof Server) {
        assertEquals(7, l.getBandwidth());
      } else {
        assertEquals(8, l.getBandwidth());
      }
    }
  }

  @Test
  public void testDepths() {
    final TwoTierConfig config = new TwoTierConfig();
    final TwoTierNetworkGenerator gen = new TwoTierNetworkGenerator(config);
    gen.createNetwork("sub", false);

    for (final Node n : ModelFacade.getInstance().getNetworkById("sub").getNodes()) {
      if (n instanceof Server) {
        assertEquals(2, ((Server) n).getDepth());
      } else if (n instanceof Switch) {
        // First (and only) core switch has a '0' in its name
        if (n.getName().contains("0")) {
          assertEquals(0, ((Switch) n).getDepth());
        } else {
          assertEquals(1, ((Switch) n).getDepth());
        }
      } else {
        fail("Node type should not be part of a TwoTierNetwork.");
      }
    }
  }

  @Test
  public void testLinkConnections() {
    final OneTierConfig rackConfig = new OneTierConfig(1, 1, false, 0, 0, 0, 0);
    final TwoTierConfig config = new TwoTierConfig();
    config.setRack(rackConfig);
    config.setNumberOfRacks(1);
    final TwoTierNetworkGenerator gen = new TwoTierNetworkGenerator(config);
    gen.createNetwork("sub", false);

    final List<Link> links = ModelFacade.getInstance().getAllLinksOfNetwork("sub");
    assertEquals("sw_0", links.get(0).getSource().getName());
    assertEquals("sw_1", links.get(0).getTarget().getName());
    assertEquals("sw_1", links.get(1).getSource().getName());
    assertEquals("sw_0", links.get(1).getTarget().getName());
    assertEquals("sw_1", links.get(2).getSource().getName());
    assertEquals("srv_2", links.get(2).getTarget().getName());
    assertEquals("srv_2", links.get(3).getSource().getName());
    assertEquals("sw_1", links.get(3).getTarget().getName());
  }

  @Test
  public void testPathsSourceAndTargetNotNull() {
    final TwoTierConfig config = new TwoTierConfig();
    final TwoTierNetworkGenerator gen = new TwoTierNetworkGenerator(config);
    gen.createNetwork("sub", false);

    for (final Path p : ModelFacade.getInstance().getNetworkById("sub").getPaths()) {
      assertNotNull(p.getSource());
      assertNotNull(p.getTarget());
    }
  }

  @Ignore
  @Test
  public void testSwitchesConnected() {
    // TODO: Implement after implementing the feature.
  }

  @Test
  public void allowNetworkIdAlreadyExists() {
    final TwoTierConfig config = new TwoTierConfig();
    ModelFacade.getInstance().addNetworkToRoot("a", false);

    final TwoTierNetworkGenerator gen = new TwoTierNetworkGenerator(config);
    gen.createNetwork("a", false);

    assertFalse(ModelFacade.getInstance().getNetworkById("a").getNodes().isEmpty());
    assertFalse(ModelFacade.getInstance().getNetworkById("a").getLinks().isEmpty());
    assertFalse(ModelFacade.getInstance().getNetworkById("a").getPaths().isEmpty());
  }

  /*
   * Negative tests
   */

  @Test
  public void rejectConfigIsNull() {
    assertThrows(IllegalArgumentException.class, () -> {
      new TwoTierNetworkGenerator(null);
    });
  }

  @Test
  public void rejectMoreThanOneRackSwitchPerRack() {
    final OneTierConfig rackConfig = new OneTierConfig(1, 2, false, 0, 0, 0, 0);
    final TwoTierConfig config = new TwoTierConfig();
    config.setRack(rackConfig);
    config.setNumberOfRacks(1);
    final TwoTierNetworkGenerator gen = new TwoTierNetworkGenerator(config);

    assertThrows(UnsupportedOperationException.class, () -> {
      gen.createNetwork("sub", false);
    });
  }

}