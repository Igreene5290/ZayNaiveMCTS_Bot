/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package ZayNMCTS;

/**
 *
 * @author igree
 */
import ai.mcts.MCTSNode;
import static ai.mcts.MCTSNode.r;
import java.math.BigInteger;
import java.util.*;
import rts.*;
import rts.units.Unit;
import util.Pair;
import util.Sampler;

/**
 *
 * @author santi
 */
public class ZayNMCTSNode extends MCTSNode {
    
    public static final int E_GREEDY = 0;
    public static final int UCB1 = 1;
    // Float to discourage long search trees
    public static float Reward_mult = 1.2f;
    
    static public int DEBUG = 0;
    
    public static float C = 0.05f;   // exploration constant for UCB1
    
    boolean forceExplorationOfNonSampledActions = true;
    public PlayerActionGenerator moveGenerator;
    HashMap<BigInteger,ZayNMCTSNode> childrenMap = new LinkedHashMap<>();    // associates action codes with children
    // Decomposition of the player actions in unit actions, and their contributions:
    public List<ZayUnitActionTableEntry> unitActionTable;
    double evaluation_bound;    // this is the maximum positive value that the evaluation function can return
    public BigInteger multipliers[];


    public ZayNMCTSNode(int maxplayer, int minplayer, GameState a_gs, ZayNMCTSNode a_parent, double a_evaluation_bound, int a_creation_ID, boolean fensa, float reward_mult) throws Exception {
        parent = a_parent;
        gs = a_gs;
        Reward_mult = reward_mult;
        if (parent==null) depth = 0;
                     else depth = parent.depth+1;     
        evaluation_bound = a_evaluation_bound;
        creation_ID = a_creation_ID;
        forceExplorationOfNonSampledActions = fensa;
        
        while (gs.winner() == -1 &&
               !gs.gameover() &&
               !gs.canExecuteAnyAction(maxplayer) &&
               !gs.canExecuteAnyAction(minplayer)) {
            gs.cycle();
        }
        if (gs.winner() != -1 || gs.gameover()) {
            type = -1;
        } else if (gs.canExecuteAnyAction(maxplayer)) {
            type = 0;
            moveGenerator = new PlayerActionGenerator(gs, maxplayer);
            actions = new ArrayList<>();
            children = new ArrayList<>();
            unitActionTable = new LinkedList<>();
            multipliers = new BigInteger[moveGenerator.getChoices().size()];
            BigInteger baseMultiplier = BigInteger.ONE;
            int idx = 0;
            for (Pair<Unit, List<UnitAction>> choice : moveGenerator.getChoices()) {
                ZayUnitActionTableEntry ae = new ZayUnitActionTableEntry();
                ae.u = choice.m_a;
                ae.nactions = choice.m_b.size();
                ae.actions = choice.m_b;
                ae.accum_evaluation = new double[ae.nactions];
                ae.visit_count = new int[ae.nactions];
                for (int i = 0; i < ae.nactions; i++) {
                    ae.accum_evaluation[i] = 0;
                    ae.visit_count[i] = 0;
                }
                unitActionTable.add(ae);
                multipliers[idx] = baseMultiplier;
                baseMultiplier = baseMultiplier.multiply(BigInteger.valueOf(ae.nactions));
                idx++;
             }
        } else if (gs.canExecuteAnyAction(minplayer)) {
            type = 1;
            moveGenerator = new PlayerActionGenerator(gs, minplayer);
            actions = new ArrayList<>();
            children = new ArrayList<>();
            unitActionTable = new LinkedList<>();
            multipliers = new BigInteger[moveGenerator.getChoices().size()];
            BigInteger baseMultiplier = BigInteger.ONE;
            int idx = 0;
            for (Pair<Unit, List<UnitAction>> choice : moveGenerator.getChoices()) {
                ZayUnitActionTableEntry ae = new ZayUnitActionTableEntry();
                ae.u = choice.m_a;
                ae.nactions = choice.m_b.size();
                ae.actions = choice.m_b;
                ae.accum_evaluation = new double[ae.nactions];
                ae.visit_count = new int[ae.nactions];
                for (int i = 0; i < ae.nactions; i++) {
                    ae.accum_evaluation[i] = 0;
                    ae.visit_count[i] = 0;
                }
                unitActionTable.add(ae);
                multipliers[idx] = baseMultiplier;
                baseMultiplier = baseMultiplier.multiply(BigInteger.valueOf(ae.nactions));
                idx++;
           }
        } else {
            type = -1;
            System.err.println("NaiveMCTSNode: This should not have happened...");
        }
    }

    
    // Naive Sampling:
    public ZayNMCTSNode selectLeaf(int maxplayer, int minplayer, float epsilon_l, float epsilon_g, float epsilon_0, int global_strategy, int max_depth, int a_creation_ID) throws Exception {
        if (unitActionTable == null) return this;
        if (depth>=max_depth) return this;       
        
        /*
        // DEBUG:
        for(PlayerAction a:actions) {
            for(Pair<Unit,UnitAction> tmp:a.getActions()) {
                if (!gs.getUnits().contains(tmp.m_a)) new Error("DEBUG!!!!");
                boolean found = false;
                for(UnitActionTableEntry e:unitActionTable) {
                    if (e.u == tmp.m_a) found = true;
                }
                if (!found) new Error("DEBUG 2!!!!!");
            }
        } 
        */
        
        if (!children.isEmpty() && r.nextFloat()>=epsilon_0) {
            // sample from the global MAB:
            ZayNMCTSNode selected = null;
            if (global_strategy==E_GREEDY) selected = selectFromAlreadySampledEpsilonGreedy(epsilon_g);
            else if (global_strategy==UCB1) selected = selectFromAlreadySampledUCB1(C);
            return selected.selectLeaf(maxplayer, minplayer, epsilon_l, epsilon_g, epsilon_0, global_strategy, max_depth, a_creation_ID);
        }  else {
            // sample from the local MABs (this might recursively call "selectLeaf" internally):
            return selectLeafUsingLocalMABs(maxplayer, minplayer, epsilon_l, epsilon_g, epsilon_0, global_strategy, max_depth, a_creation_ID);
        }
    }
   

    
    public ZayNMCTSNode selectFromAlreadySampledEpsilonGreedy(float epsilon_g) throws Exception {
        if (r.nextFloat()>=epsilon_g) {
            ZayNMCTSNode best = null;
            for(MCTSNode pate:children) {
                if (type==0) {
                    // max node:
                    if (best==null || (pate.accum_evaluation/pate.visit_count)>(best.accum_evaluation/best.visit_count)) {
                        best = (ZayNMCTSNode)pate;
                    }                    
                } else {
                    // min node:
                    if (best==null || (pate.accum_evaluation/pate.visit_count)<(best.accum_evaluation/best.visit_count)) {
                        best = (ZayNMCTSNode)pate;
                    }                                        
                }
            }

            return best;
        } else {
            // choose one at random from the ones seen so far:
            ZayNMCTSNode best = (ZayNMCTSNode)children.get(r.nextInt(children.size()));
            return best;
        }
    }
    
    
    public ZayNMCTSNode selectFromAlreadySampledUCB1(float C) throws Exception {
        ZayNMCTSNode best = null;
        double bestScore = 0;
        for(MCTSNode pate:children) {
            double exploitation = ((double)pate.accum_evaluation) / pate.visit_count;
            double exploration = Math.sqrt(Math.log((double)visit_count)/pate.visit_count);
            if (type==0) {
                // max node:
                exploitation = (evaluation_bound + exploitation)/(2*evaluation_bound);
            } else {
                exploitation = (evaluation_bound - exploitation)/(2*evaluation_bound);
            }
    //            System.out.println(exploitation + " + " + exploration);

            double tmp = C*exploitation + exploration;            
            if (best==null || tmp>bestScore) {
                best = (ZayNMCTSNode)pate;
                bestScore = tmp;
            }
        }
        
        return best;
    }    
    
    
    public ZayNMCTSNode selectLeafUsingLocalMABs(int maxplayer, int minplayer, float epsilon_l, float epsilon_g, float epsilon_0, int global_strategy, int max_depth, int a_creation_ID) throws Exception {   
        PlayerAction pa2;
        BigInteger actionCode;       

        // For each unit, rank the unitActions according to preference:
        List<double []> distributions = new LinkedList<>();
        List<Integer> notSampledYet = new LinkedList<>();
        for(ZayUnitActionTableEntry ate:unitActionTable) {
            double []dist = new double[ate.nactions];
            int bestIdx = -1;
            double bestEvaluation = 0;
            int visits = 0;
            for(int i = 0;i<ate.nactions;i++) {
                if (type==0) {
                    // max node:
                    if (bestIdx==-1 || 
                        (visits!=0 && ate.visit_count[i]==0) ||
                        (visits!=0 && (ate.accum_evaluation[i]/ate.visit_count[i])>bestEvaluation)) {
                        bestIdx = i;
                        if (ate.visit_count[i]>0) bestEvaluation = (ate.accum_evaluation[i]/ate.visit_count[i]);
                                             else bestEvaluation = 0;
                        visits = ate.visit_count[i];
                    }
                } else {
                    // min node:
                    if (bestIdx==-1 || 
                        (visits!=0 && ate.visit_count[i]==0) ||
                        (visits!=0 && (ate.accum_evaluation[i]/ate.visit_count[i])<bestEvaluation)) {
                        bestIdx = i;
                        if (ate.visit_count[i]>0) bestEvaluation = (ate.accum_evaluation[i]/ate.visit_count[i]);
                                             else bestEvaluation = 0;
                        visits = ate.visit_count[i];
                    }
                }
                dist[i] = epsilon_l/ate.nactions;
            }
            if (ate.visit_count[bestIdx]!=0) {
                dist[bestIdx] = (1-epsilon_l) + (epsilon_l/ate.nactions);
            } else {
                if (forceExplorationOfNonSampledActions) {
                    for(int j = 0;j<dist.length;j++) 
                        if (ate.visit_count[j]>0) dist[j] = 0;
                }
            }  

            if (DEBUG>=3) {
                System.out.print("[ ");
                for(int i = 0;i<ate.nactions;i++) System.out.print("(" + ate.visit_count[i] + "," + ate.accum_evaluation[i]/ate.visit_count[i] + ")");
                System.out.println("]");
                System.out.print("[ ");
                for (double v : dist) System.out.print(v + " ");
                System.out.println("]");
            }

            notSampledYet.add(distributions.size());
            distributions.add(dist);
        }

        // Select the best combination that results in a valid playeraction by epsilon-greedy sampling:
        ResourceUsage base_ru = new ResourceUsage();
        for(Unit u:gs.getUnits()) {
            UnitAction ua = gs.getUnitAction(u);
            if (ua!=null) {
                ResourceUsage ru = ua.resourceUsage(u, gs.getPhysicalGameState());
                base_ru.merge(ru);
            }
        }

        pa2 = new PlayerAction();
        actionCode = BigInteger.ZERO;
        pa2.setResourceUsage(base_ru.clone());            
        while(!notSampledYet.isEmpty()) {
            int i = notSampledYet.remove(r.nextInt(notSampledYet.size()));

            try {
                ZayUnitActionTableEntry ate = unitActionTable.get(i);
                int code;
                UnitAction ua;
                ResourceUsage r2;

                // try one at random:
                double []distribution = distributions.get(i);
                code = Sampler.weighted(distribution);
                ua = ate.actions.get(code);
                r2 = ua.resourceUsage(ate.u, gs.getPhysicalGameState());
                if (!pa2.getResourceUsage().consistentWith(r2, gs)) {
                    // sample at random, eliminating the ones that have not worked so far:
                    List<Double> dist_l = new ArrayList<>();
                    List<Integer> dist_outputs = new ArrayList<>();

                    for(int j = 0;j<distribution.length;j++) {
                        dist_l.add(distribution[j]);
                        dist_outputs.add(j);
                    }
                    do{
                        int idx = dist_outputs.indexOf(code);
                        dist_l.remove(idx);
                        dist_outputs.remove(idx);
                        code = (Integer)Sampler.weighted(dist_l, dist_outputs);
                        ua = ate.actions.get(code);
                        r2 = ua.resourceUsage(ate.u, gs.getPhysicalGameState());                            
                    }while(!pa2.getResourceUsage().consistentWith(r2, gs));
                }

                // DEBUG code:
                if (gs.getUnit(ate.u.getID())==null) throw new Error("Issuing an action to an inexisting unit!!!");
               

                pa2.getResourceUsage().merge(r2);
                pa2.addUnitAction(ate.u, ua);

                actionCode = actionCode.add(BigInteger.valueOf(code).multiply(multipliers[i]));

            } catch(Exception e) {
            }
        }   

        ZayNMCTSNode pate = childrenMap.get(actionCode);
        if (pate==null) {
            actions.add(pa2);            
            GameState gs2 = gs.cloneIssue(pa2);
            ZayNMCTSNode node = new ZayNMCTSNode(maxplayer, minplayer, gs2.clone(), this, evaluation_bound, a_creation_ID, forceExplorationOfNonSampledActions, Reward_mult);
            childrenMap.put(actionCode,node);
            children.add(node);          
            return node;                
        }

        return pate.selectLeaf(maxplayer, minplayer, epsilon_l, epsilon_g, epsilon_0, global_strategy, max_depth, a_creation_ID);
    }
    
    
    public ZayUnitActionTableEntry getActionTableEntry(Unit u) {
        for(ZayUnitActionTableEntry e:unitActionTable) {
            if (e.u == u) return e;
        }
        throw new Error("Could not find Action Table Entry!");
    }


    public void propagateEvaluation(double evaluation, ZayNMCTSNode child) {
        accum_evaluation += evaluation;
        visit_count++;
        
//        if (child!=null) System.out.println(evaluation);

        // update the unitAction table:
        if (child != null) {
            int idx = children.indexOf(child);
            PlayerAction pa = actions.get(idx);

            for (Pair<Unit, UnitAction> ua : pa.getActions()) {
                ZayUnitActionTableEntry actionTable = getActionTableEntry(ua.m_a);
                idx = actionTable.actions.indexOf(ua.m_b);

                if (idx==-1) {
                    System.out.println("Looking for action: " + ua.m_b);
                    System.out.println("Available actions are: " + actionTable.actions);
                }
                
                actionTable.accum_evaluation[idx] += evaluation;
                actionTable.visit_count[idx]++;
            }
        }

        if (parent != null) {
            ((ZayNMCTSNode)parent).propagateEvaluation(evaluation * Reward_mult, this);
        }
    }

    public void printUnitActionTable() {
        for (ZayUnitActionTableEntry uat : unitActionTable) {
            System.out.println("Actions for unit " + uat.u);
            for (int i = 0; i < uat.nactions; i++) {
                System.out.println("   " + uat.actions.get(i) + " visited " + uat.visit_count[i] + " with average evaluation " + (uat.accum_evaluation[i] / uat.visit_count[i]));
            }
        }
    }    
}

