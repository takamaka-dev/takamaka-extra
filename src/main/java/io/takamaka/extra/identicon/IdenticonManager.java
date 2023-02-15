/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package io.takamaka.extra.identicon;

import io.takamaka.extra.identicon.exceptions.IdenticonException;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.logging.Level;
import java.util.stream.IntStream;
import lombok.extern.slf4j.Slf4j;

/**
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
@Slf4j
public class IdenticonManager {

    //              
    private static int[][][][] blocks;
    
    private IdenticonManager() {
        
        blocks = new int[IdentiBaseBlocks.BLOCKS.length][][][];
        //for each block in array
        ConcurrentSkipListMap<String, IdenticonException> errors = new ConcurrentSkipListMap<>();
        IntStream.range(0, blocks.length).forEach((int blockIndex) -> {
            blocks[blockIndex] = new int[4][][];
            //for each rotation in array
            IntStream.range(0, 4).forEach((int rotationIndex) -> {
                switch (rotationIndex) {
                    case 0: //original no rotation
                        blocks[blockIndex][rotationIndex] = IdentiColorHelper.clone(IdentiBaseBlocks.BLOCKS[blockIndex]);
                        break;
                    case 1: //mirror vertical 
                        blocks[blockIndex][rotationIndex] = IdentiColorHelper.mirrorVertical(IdentiBaseBlocks.BLOCKS[blockIndex]);
                        break;
                    case 2: //mirror horizontal
                        blocks[blockIndex][rotationIndex] = IdentiColorHelper.mirrorHorizontal(IdentiBaseBlocks.BLOCKS[blockIndex]);
                        break;
                    case 3: //mirror V+H
                        blocks[blockIndex][rotationIndex] = IdentiColorHelper.mirrorHplusV(IdentiBaseBlocks.BLOCKS[blockIndex]);
                        break;
                    default:
                        String errMsg = "block " + blockIndex + " rotation " + rotationIndex;
                        log.error("rotation index out of range " + errMsg);
                        errors.put(errMsg, new IdenticonException("otation index out of range " + errMsg));
                    //Log.log(Level.SEVERE, "OUT OF RANGE");
                }
            });
        });
        if (!errors.isEmpty()) {
            Map.Entry<String, IdenticonException> firstEntry = errors.firstEntry();
            log.error(firstEntry.getKey(), firstEntry.getValue());
            throw new RuntimeException(firstEntry.getValue());
        }
        
    }
    
    private static class IM {
        
        public static final IdenticonManager I = new IdenticonManager();
    }
    
    public static IdenticonManager i() {
        return IM.I;
    }
    
    public int[][][][] getBlocks() {
        return blocks;
    }
    
}
