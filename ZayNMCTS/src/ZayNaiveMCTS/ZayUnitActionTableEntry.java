/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package ZayNaiveMCTS;

/**
 *
 * @author igree
 */

import java.util.List;
import rts.UnitAction;
import rts.units.Unit;

public class ZayUnitActionTableEntry {
    public Unit u;
    public int nactions = 0;
    public List<UnitAction> actions;
    public double[] accum_evaluation;
    public int[] visit_count;
}
