package algorithms.pm;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import algorithms.AbstractAlgorithm;
import algorithms.AlgorithmConfig;
import facade.config.ModelFacadeConfig;
import ilp.wrapper.IlpDelta;
import ilp.wrapper.IlpSolverException;
import ilp.wrapper.IncrementalIlpSolver;
import ilp.wrapper.Statistics;
import ilp.wrapper.config.IlpSolverConfig;
import ilp.wrapper.impl.IncrementalGurobiSolver;
import metrics.manager.GlobalMetricsManager;
import metrics.utils.CostUtility;
import model.Link;
import model.Node;
import model.SubstrateElement;
import model.SubstrateLink;
import model.SubstrateNetwork;
import model.SubstrateNode;
import model.SubstratePath;
import model.SubstrateServer;
import model.SubstrateSwitch;
import model.VirtualElement;
import model.VirtualLink;
import model.VirtualNetwork;
import model.VirtualNode;
import model.VirtualServer;
import model.VirtualSwitch;
import patternmatching.IncrementalPatternMatcher;
import patternmatching.PatternMatchingDelta;
import patternmatching.PatternMatchingDelta.Match;
import patternmatching.emoflon.EmoflonPatternMatcher;
import patternmatching.emoflon.EmoflonPatternMatcherFactory;

/**
 * Implementation of the model-driven virtual network algorithm that uses pattern matching as a way
 * to reduce the search space of the ILP solver.
 * 
 * Parts of this implementation are heavily inspired, taken or adapted from the idyve project [1].
 * 
 * [1] Tomaszek, S., Modellbasierte Einbettung von virtuellen Netzwerken in Rechenzentren,
 * http://dx.doi.org/10.12921/TUPRINTS-00017362. – DOI 10.12921/TUPRINTS– 00017362, 2020.
 *
 * @author Stefan Tomaszek (ES TU Darmstadt) [idyve project]
 * @author Maximilian Kratz {@literal <maximilian.kratz@stud.tu-darmstadt.de>}
 */
public class VnePmMdvneAlgorithm extends AbstractAlgorithm {

  /**
   * ILP delta generator that converts matches and given model objects into ILP constraints for the
   * solver.
   * 
   * @author Stefan Tomaszek (ES TU Darmstadt) [idyve project]
   * @author Maximilian Kratz {@literal <maximilian.kratz@stud.tu-darmstadt.de>}
   */
  private class IlpDeltaGenerator {
    /**
     * ILP delta object that holds all information.
     */
    final IlpDelta delta = new IlpDelta();

    /**
     * Adds a new match from a virtual to a substrate network.
     * 
     * @param match Match to get information from.
     */
    public void addNewNetworkMatch(final Match match) {
      delta.addVariable("rej" + match.getVirtual().getName(),
          CostUtility.getNetworkRejectionCost());
      variablesToMatch.put("rej" + match.getVirtual().getName(), match);
    }

    /**
     * Adds a match from a virtual link to a substrate server.
     * 
     * @param match Match to get information from.
     */
    public void addLinkServerMatch(final Match match) {
      final String varName = match.getVirtual().getName() + "_" + match.getSubstrate().getName();
      final VirtualLink vLink = (VirtualLink) facade.getLinkById(match.getVirtual().getName());

      // If the source node (target node) of the virtual link may not be embedded to the substrate
      // node, it's mapping variable is missing in the solver's model. Due to the fact that there is
      // no way to properly map the source node (target node) onto this substrate server, the ILP
      // solver does not have to deal with the embedding of the link for this particular substrate
      // node, to.
      final String sourceVarName =
          vLink.getSource().getName() + "_" + match.getSubstrate().getName();
      final String targetVarName =
          vLink.getTarget().getName() + "_" + match.getSubstrate().getName();

      if (!delta.hasAddVariable(sourceVarName) || !delta.hasAddVariable(targetVarName)) {
        return;
      }

      delta.addVariable(varName, getLinkCost(vLink, (SubstrateNode) match.getSubstrate()));
      delta.setVariableWeightForConstraint("vl" + match.getVirtual().getName(), 1, varName);
      delta.addLessOrEqualsConstraint("req" + varName, 0, new int[] {2, -1, -1},
          new String[] {varName, sourceVarName, targetVarName});
      variablesToMatch.put(varName, match);
    }

    /**
     * Adds a match from a virtual link to a substrate path.
     * 
     * @param match Match to get information from.
     */
    public void addLinkPathMatch(final Match match) {
      final VirtualLink vLink = (VirtualLink) facade.getLinkById(match.getVirtual().getName());
      final SubstratePath sPath =
          (SubstratePath) facade.getPathById(match.getSubstrate().getName());

      // If the source node (target node) of the virtual link may not be embedded to the substrate
      // paths source node (target node), it's mapping variable is missing in the solver's model.
      // Due to the fact that there is no way to properly map the source node (target node) onto the
      // substrate source node (target node), the ILP solver does not have to deal with the
      // embedding of the link for this particular substrate node, to.
      // This may e.g. be the case if the virtual node is a server but the substrate node is a
      // switch.
      final String sourceVarName = vLink.getSource().getName() + "_" + sPath.getSource().getName();
      final String targetVarName = vLink.getTarget().getName() + "_" + sPath.getTarget().getName();

      if (!delta.hasAddVariable(sourceVarName) || !delta.hasAddVariable(targetVarName)) {
        return;
      }

      final String varName = match.getVirtual().getName() + "_" + match.getSubstrate().getName();

      delta.addVariable(varName, getLinkCost(vLink, sPath));
      delta.setVariableWeightForConstraint("vl" + match.getVirtual().getName(), 1, varName);
      delta.addLessOrEqualsConstraint("req" + varName, 0, new int[] {2, -1, -1},
          new String[] {varName, sourceVarName, targetVarName});
      forEachLink(sPath, l -> delta.setVariableWeightForConstraint("sl" + l.getName(),
          vLink.getBandwidth(), varName));
      variablesToMatch.put(varName, match);
    }

    /**
     * Adds a (positive) match from a virtual server to a substrate server.
     * 
     * @param match Match to get information from.
     */
    public void addServerMatch(final Match match) {
      final VirtualServer vServer =
          (VirtualServer) facade.getServerById(match.getVirtual().getName());
      final String varName = match.getVirtual().getName() + "_" + match.getSubstrate().getName();
      delta.addVariable(varName, getNodeCost(vServer, (SubstrateServer) match.getSubstrate()));
      delta.setVariableWeightForConstraint("vs" + match.getVirtual().getName(), 1, varName);

      delta.setVariableWeightForConstraint("cpu" + match.getSubstrate().getName(), vServer.getCpu(),
          varName);
      delta.setVariableWeightForConstraint("mem" + match.getSubstrate().getName(),
          vServer.getMemory(), varName);
      delta.setVariableWeightForConstraint("sto" + match.getSubstrate().getName(),
          vServer.getStorage(), varName);
      variablesToMatch.put(varName, match);
    }

    /**
     * Adds a match from a virtual switch to a substrate switch.
     * 
     * @param match Match to get information from.
     */
    public void addSwitchMatch(final Match match) {
      final String varName = match.getVirtual().getName() + "_" + match.getSubstrate().getName();
      delta.addVariable(varName,
          getNodeCost((VirtualNode) match.getVirtual(), (SubstrateNode) match.getSubstrate()));
      delta.setVariableWeightForConstraint("vw" + match.getVirtual().getName(), 1, varName);
      variablesToMatch.put(varName, match);
    }

    /**
     * Adds a new substrate server.
     * 
     * @param server SubstrateServer to get information from.
     */
    public void addNewSubstrateServer(final SubstrateServer server) {
      delta.addLessOrEqualsConstraint("cpu" + server.getName(), server.getResidualCpu());
      delta.addLessOrEqualsConstraint("mem" + server.getName(), server.getResidualMemory());
      delta.addLessOrEqualsConstraint("sto" + server.getName(), server.getResidualStorage());
    }

    /**
     * Adds a new substrate link.
     * 
     * @param link SubstrateLink to get information from.
     */
    public void addNewSubstrateLink(final SubstrateLink link) {
      delta.addLessOrEqualsConstraint("sl" + link.getName(), link.getResidualBandwidth());
    }

    /**
     * Adds a new virtual server.
     * 
     * @param server VirtualServer to get information from.
     */
    public void addNewVirtualServer(final VirtualServer server) {
      delta.addEqualsConstraint("vs" + server.getName(), 1);
      delta.setVariableWeightForConstraint("vs" + server.getName(), 1,
          "rej" + server.getNetwork().getName());
    }

    /**
     * Adds a new virtual switch.
     * 
     * @param sw VirtualSwitch to get information from.
     */
    public void addNewVirtualSwitch(final VirtualSwitch sw) {
      delta.addEqualsConstraint("vw" + sw.getName(), 1);
      delta.setVariableWeightForConstraint("vw" + sw.getName(), 1,
          "rej" + sw.getNetwork().getName());
    }

    /**
     * Adds a new virtual link.
     * 
     * @param link VirtualLink to get information from.
     */
    public void addNewVirtualLink(final VirtualLink link) {
      delta.addEqualsConstraint("vl" + link.getName(), 1);
      delta.setVariableWeightForConstraint("vl" + link.getName(), 1,
          "rej" + link.getNetwork().getName());
    }

    /**
     * Applies the delta to the ILP solver object.
     */
    public void apply() {
      delta.apply(ilpSolver);
    }

  }

  /**
   * Algorithm instance (singleton).
   */
  private static VnePmMdvneAlgorithm instance;

  /**
   * Incremental pattern matcher to use.
   */
  private IncrementalPatternMatcher patternMatcher;

  /**
   * Incremental ILP solver to use.
   */
  private IncrementalIlpSolver ilpSolver;

  /**
   * Mapping of string (name) to matches.
   */
  private final Map<String, Match> variablesToMatch = new HashMap<>();

  /**
   * Constructor that gets the substrate as well as the virtual network.
   * 
   * @param sNet Substrate network to work with.
   * @param vNets Set of virtual networks to work with.
   */
  private VnePmMdvneAlgorithm(final SubstrateNetwork sNet, final Set<VirtualNetwork> vNets) {
    super(sNet, vNets);
  }

  /**
   * Initializes a new instance of the VNE pattern matching algorithm.
   * 
   * @param sNet Substrate network to work with.
   * @param vNets Set of virtual networks to work with.
   * @return Instance of this algorithm implementation.
   */
  public static VnePmMdvneAlgorithm prepare(final SubstrateNetwork sNet,
      final Set<VirtualNetwork> vNets) {
    if (sNet == null || vNets == null) {
      throw new IllegalArgumentException("One of the provided network objects was null.");
    }

    if (vNets.size() == 0) {
      throw new IllegalArgumentException("Provided set of virtual networks was empty.");
    }

    if (instance == null) {
      instance = new VnePmMdvneAlgorithm(sNet, vNets);
    }
    instance.sNet = sNet;
    instance.vNets = new HashSet<VirtualNetwork>();
    instance.vNets.addAll(vNets);

    instance.checkPreConditions();
    return instance;
  }

  /**
   * Resets the ILP solver and the pattern matcher.
   */
  public void dispose() {
    if (instance == null) {
      return;
    }
    if (this.ilpSolver != null) {
      this.ilpSolver.dispose();
    }
    if (this.patternMatcher != null) {
      this.patternMatcher.dispose();
    }
    instance = null;
  }

  @Override
  public boolean execute() {
    init();

    // Repair model consistency: Substrate network
    repairSubstrateNetwork();

    // Repair model consistency: Virtual network(s)
    final Set<VirtualNetwork> repairedVnets = repairVirtualNetworks();
    if (!repairedVnets.isEmpty()) {
      this.patternMatcher = new EmoflonPatternMatcherFactory().create();
    }
    vNets.addAll(repairedVnets);

    GlobalMetricsManager.startPmTime();
    final PatternMatchingDelta delta = patternMatcher.run();
    GlobalMetricsManager.endPmTime();

    final IlpDeltaGenerator gen = new IlpDeltaGenerator();

    // add new elements
    addElementsToSolver(gen);

    // add new matches
    delta.getNewServerMatchPositives().stream()
        .filter(m -> vNets.contains(((VirtualServer) m.getVirtual()).getNetwork()))
        .forEach(gen::addServerMatch);
    delta.getNewSwitchMatchPositives().stream()
        .filter(m -> vNets.contains(((VirtualSwitch) m.getVirtual()).getNetwork()))
        .forEach(gen::addSwitchMatch);;

    // Important: Due to the fact that both link constraint generating methods check the existence
    // of the node mapping variables, the link constraints have to be added *after* all node
    // constraints.
    delta.getNewLinkPathMatchPositives().stream()
        .filter(m -> vNets.contains(((VirtualLink) m.getVirtual()).getNetwork()))
        .forEach(gen::addLinkPathMatch);;
    delta.getNewLinkServerMatchPositives().stream()
        .filter(m -> vNets.contains(((VirtualLink) m.getVirtual()).getNetwork()))
        .forEach(gen::addLinkServerMatch);;

    // apply delta in ILP generator
    gen.apply();

    GlobalMetricsManager.startIlpTime();
    final Statistics solve = ilpSolver.solve();
    GlobalMetricsManager.endIlpTime();
    Set<VirtualNetwork> rejectedNetworks = null;
    if (solve.isFeasible()) {
      GlobalMetricsManager.startDeployTime();
      rejectedNetworks = updateMappingsAndEmbed(ilpSolver.getMappings());
    } else {
      throw new IlpSolverException("Problem was infeasible.");
    }

    embedNetworks(rejectedNetworks);
    GlobalMetricsManager.endDeployTime();
    return rejectedNetworks.isEmpty();
  }

  /*
   * Helper methods.
   */

  /**
   * Repairs the consistency of the substrate network. This is necessary, if a virtual network was
   * removed "dirty" from the model and the residual values or guest references are not updated
   * properly.
   */
  private void repairSubstrateNetwork() {
    // Find all networks that were removed in the meantime
    final Set<VirtualNetwork> removedGuests = sNet.getGuests().stream()
        .filter(g -> !facade.networkExists(g.getName())).collect(Collectors.toSet());

    // Remove embedding of all elements of the virtual network
    removedGuests.forEach(g -> facade.unembedVirtualNetwork(g));
  }

  /**
   * Repairs the consistency of all virtual networks. This is necessary if a substrate server was
   * removed "dirty" from the model and the previously embedded virtual network is in a floating
   * state. After detecting this state, the virtual network's embedding will be removed and it has
   * to be embedded again.
   * 
   * @return Set of virtual networks that have to be embedded again, because their old embedding was
   *         invalid.
   */
  private Set<VirtualNetwork> repairVirtualNetworks() {
    // Find all virtual networks that are floating
    final Set<VirtualNetwork> floatingGuests = sNet.getGuests().stream()
        .filter(g -> facade.checkIfFloating(g)).collect(Collectors.toSet());

    // Remove embedding of all elements of the virtual network so they can be embedded again
    floatingGuests.forEach(g -> facade.unembedVirtualNetwork(g));
    return floatingGuests;
  }

  /**
   * Checks every condition necessary to run this algorithm. If a condition is not met, it throws an
   * UnsupportedOperationException.
   */
  private void checkPreConditions() {
    // Path creation has to be enabled for paths with length = 1
    if (ModelFacadeConfig.MIN_PATH_LENGTH != 1) {
      throw new UnsupportedOperationException("Minimum path length must be 1.");
    }

    // There must be generated substrate paths
    if (sNet.getPaths().isEmpty()) {
      throw new UnsupportedOperationException("Generated paths are missing in substrate network.");
    }
  }

  /**
   * Embeds all virtual networks that are not part of the given rejected networks set to the
   * substrate network.
   * 
   * @param rejectedNetworks Set of virtual networks that could not be embedded.
   */
  private void embedNetworks(final Set<VirtualNetwork> rejectedNetworks) {
    for (final VirtualNetwork vNet : vNets) {
      if (!rejectedNetworks.contains(vNet)) {
        facade.embedNetworkToNetwork(sNet.getName(), vNet.getName());
      }
    }
  }

  /**
   * Adds the elements of the substrate and the virtual network to the given delta generator
   * (solver).
   * 
   * @param gen ILP delta generator to add elements to.
   */
  private void addElementsToSolver(final IlpDeltaGenerator gen) {
    // Substrate network
    for (final Node n : sNet.getNodes()) {
      if (n instanceof SubstrateServer) {
        gen.addNewSubstrateServer((SubstrateServer) n);
      } else if (n instanceof SubstrateSwitch) {
        // Nothing to do here
      }
    }

    for (final Link l : sNet.getLinks()) {
      if (l instanceof SubstrateLink) {
        gen.addNewSubstrateLink((SubstrateLink) l);
      }
    }

    // Virtual networks
    final Iterator<VirtualNetwork> it = vNets.iterator();
    while (it.hasNext()) {
      final VirtualNetwork vNet = it.next();

      for (final Node n : vNet.getNodes()) {
        if (n instanceof VirtualServer) {
          gen.addNewVirtualServer((VirtualServer) n);
        } else if (n instanceof VirtualSwitch) {
          gen.addNewVirtualSwitch((VirtualSwitch) n);
        }
      }

      for (final Link l : vNet.getLinks()) {
        if (l instanceof VirtualLink) {
          gen.addNewVirtualLink((VirtualLink) l);
        }
      }

      // Network match
      gen.addNewNetworkMatch(new Match(vNet, sNet));
    }
  }

  /**
   * Updates and embeds the actual mappings for a given map of names (strings) and booleans.
   * 
   * @param mappings Map of strings and booleans. The keys are mapping names and the values define
   *        if the mapping was chosen.
   * @return Returns a set of all virtual networks that could not be embedded.
   */
  private Set<VirtualNetwork> updateMappingsAndEmbed(final Map<String, Boolean> mappings) {
    // Embed elements
    final Set<VirtualNetwork> rejectedNetworks = new HashSet<VirtualNetwork>();
    final EmoflonPatternMatcher engine = (EmoflonPatternMatcher) patternMatcher;

    // for (final String s : newMappings) {
    for (final String s : mappings.keySet()) {
      if (!mappings.get(s)) {
        continue;
      }

      final Match m = variablesToMatch.get(s);

      // Network -> Network (rejected)
      if (m.getVirtual() instanceof VirtualNetwork) {
        rejectedNetworks.add((VirtualNetwork) m.getVirtual());
        continue;
      }

      // Embed element: Either use emoflon/GT or use manual mode.
      switch (AlgorithmConfig.emb) {
        case EMOFLON:
          // Create embedding via matches and graph transformation
          engine.apply((VirtualElement) m.getVirtual(), (SubstrateElement) m.getSubstrate(), true);
          updatePathLinks(m);
          break;
        case EMOFLON_WO_UPDATE:
          // Create embedding via matches and graph transformation
          engine.apply((VirtualElement) m.getVirtual(), (SubstrateElement) m.getSubstrate(), false);
          updatePathLinks(m);
          break;
        case MANUAL:
          final VirtualElement ve = (VirtualElement) m.getVirtual();
          final SubstrateElement se = (SubstrateElement) m.getSubstrate();
          if (ve instanceof VirtualServer) {
            facade.embedServerToServer(se.getName(), ve.getName());
          } else if (ve instanceof VirtualSwitch) {
            facade.embedSwitchToNode(se.getName(), ve.getName());
          } else if (ve instanceof VirtualLink) {
            if (se instanceof SubstrateServer) {
              facade.embedLinkToServer(se.getName(), ve.getName());
            } else if (se instanceof SubstratePath) {
              facade.embedLinkToPath(se.getName(), ve.getName());
            }
          }
          break;
      }
    }

    return rejectedNetworks;
  }

  /**
   * FIXME!
   * 
   * If substrate element is a path, we have to update the residual bandwidths of all links, because
   * eMoflon does not support 'for-each' like operations yet. (Please also see 'embeddingRules.gt').
   * If eMoflon supports this feature and the rule in 'embeddingRules.gt' got updated, this method
   * and all of its calls must be removed.
   * 
   * @param match Match to check and update.
   */
  private void updatePathLinks(final Match match) {
    if (match.getSubstrate() instanceof SubstratePath) {
      final SubstratePath subPath = (SubstratePath) match.getSubstrate();
      final VirtualLink virtLink = (VirtualLink) match.getVirtual();

      subPath.getLinks().stream().forEach(l -> {
        final SubstrateLink sl = (SubstrateLink) l;
        sl.setResidualBandwidth(sl.getResidualBandwidth() - virtLink.getBandwidth());
      });
    }
  }

  /**
   * Initializes the algorithm by creating a new incremental solver object and a new pattern matcher
   * object.
   */
  public void init() {
    // Create new ILP solver object on every method call.
    ilpSolver = new IncrementalGurobiSolver(IlpSolverConfig.TIME_OUT, IlpSolverConfig.RANDOM_SEED);

    if (patternMatcher == null) {
      patternMatcher = new EmoflonPatternMatcherFactory().create();
    }
  }

  public void forEachLink(final SubstratePath sPath, final Consumer<? super Link> operation) {
    sPath.getLinks().stream().forEach(operation);
  }

  /*
   * Cost functions.
   */

  public double getCost(final VirtualElement virt, final SubstrateElement host) {
    if (virt instanceof Link) {
      return getLinkCost((VirtualLink) virt, host);
    } else if (virt instanceof Node && host instanceof Node) {
      return getNodeCost((VirtualNode) virt, (SubstrateNode) host);
    }

    throw new IllegalArgumentException();
  }

  public double getNodeCost(final VirtualNode virt, final SubstrateNode sub) {
    switch (AlgorithmConfig.obj) {
      case TOTAL_PATH_COST:
        return CostUtility.getTotalPathCostNode(virt, sub);
      case TOTAL_COMMUNICATION_COST_A:
        return CostUtility.getTotalCommunicationCostNode();
      case TOTAL_COMMUNICATION_COST_B:
        return CostUtility.getTotalCommunicationCostNode();
      default:
        throw new UnsupportedOperationException();
    }
  }

  public double getLinkCost(final VirtualLink virt, final SubstrateElement sub) {
    switch (AlgorithmConfig.obj) {
      case TOTAL_PATH_COST:
        return CostUtility.getTotalPathCostLink(sub);
      case TOTAL_COMMUNICATION_COST_A:
        return CostUtility.getTotalCommunicationCostLinkA(virt, sub);
      case TOTAL_COMMUNICATION_COST_B:
        return CostUtility.getTotalCommunicationCostLinkB(virt, sub);
      default:
        throw new UnsupportedOperationException();
    }
  }

}