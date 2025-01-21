/*
Created by Jiwan Kim 21/01/2025 (jiwankim@kaist.ac.kr, kjwan4435@gmail.com)
Copyright © 2025 KAIST WITLAB. All rights reserved.
 */
public class Utilities {
    static public String SUB_ID = "0";

    static public int NofBlocksReps = 1;
    static public int NofBlocks = NofBlocksReps * 3; // 3 conditions
    static public int BlockCounter = 0;
    static public int TrialCounter = 0;
    static public int TrialEndCounter = 0;
    static public int CurrentSelectionMethod = 0;

    static public int TargetWidth = 1;
    static public int TargetDistance = 1;
    static public int TargetReps = 2;
    static public int NofTrials = TargetWidth*TargetDistance*TargetReps;

    static public int[] TargetWidths = {120};
    static public int[] TargetDistances = {200};
}
