/*
 * The MIT License
 *
 * Copyright (c) 2012 Tobias Marschall
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package simseq;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.Random;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.FileInputStream;

/** A probability distribution over a set of possible insert sizes,
  * that is, the size of the whole fragment (including the reads).
  */
public class InsertSizeDistribution {

	/** The actual internal segment sizes. */
	private ArrayList<Integer> values;
	/** Cumulative probabilities, that is, cumulativeProbabilities[i] is the probability 
	  * of observing a value from values[0] to values[i].
	  */
	private ArrayList<Double> cumulativeProbabilities;

	public static class InvalidInputFileException extends Exception {
		public InvalidInputFileException() { super(); }
		public InvalidInputFileException(String message) { super(message); }
	}

	public InsertSizeDistribution(String filename) throws FileNotFoundException, IOException, InvalidInputFileException {
		// Mapping insert sizes to their probability.
		HashMap<Integer,Double> distribution = new HashMap<Integer,Double>();
		BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(filename)));
		int lineNr = 0;
		double probabilitySum = 0.0;
		while (true) {
			String line = br.readLine();
			lineNr += 1;
			if (line==null) break;
			line = line.trim();
			if (line.equals("")) continue;
			StringTokenizer tokenizer = new StringTokenizer(line);
			if (!tokenizer.hasMoreTokens()) throw new InvalidInputFileException("Illegal insert size distribution file. Offending line: "+lineNr);
			int insert_size = Integer.parseInt(tokenizer.nextToken());
			if (!tokenizer.hasMoreTokens()) throw new InvalidInputFileException("Illegal insert size distribution file. Offending line: "+lineNr);
			double probability = Double.parseDouble(tokenizer.nextToken());
			if (tokenizer.hasMoreTokens()) throw new InvalidInputFileException("Illegal insert size distribution file. Offending line: "+lineNr);
			if (distribution.containsKey(insert_size)) throw new InvalidInputFileException("Illegal insert size distribution file: duplicate entry: "+insert_size);
			distribution.put(insert_size, probability);
			probabilitySum += probability;
		}
		if (distribution.isEmpty()) throw new InvalidInputFileException("Empty insert size distribution file.");
		if (probabilitySum <= 0.0) throw new InvalidInputFileException("Illegal insert size distribution file: probability sum not positive.");
		values = new ArrayList<Integer>();
		cumulativeProbabilities = new ArrayList<Double>();
		int i = 0;
		for (Map.Entry<Integer,Double> entry : distribution.entrySet()) {
			values.add(entry.getKey());
			cumulativeProbabilities.add(entry.getValue() / probabilitySum);
			if (i > 0) {
				cumulativeProbabilities.set(i, cumulativeProbabilities.get(i) + cumulativeProbabilities.get(i-1));
			}
			i += 1;
		}
	}

	public int sample(Random random) {
		double p = random.nextDouble();
		int i = Collections.binarySearch(cumulativeProbabilities, p);
		if (i>=0) return values.get(i);
		else return values.get(-(i+1));
	}
}
