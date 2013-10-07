/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package simseq;

import java.util.Random;

/**
 *
 * @author jstjohn
 */
public class SeqSampler {

    private String seq;
    private int len;

    public SeqSampler(String sequence) {
        this.seq = sequence;
        this.len = sequence.length();
    }

    public void SESample(SamRecord sr1, String qual1, int read1_len, String adapter1, Random r) {
        int p;
        //increment the seq index
        sr1.seqIndex++;
        sr1.first = false;
        sr1.second = false;
        sr1.paired = false;
        sr1.proper_pair = false;
        
        do {
            p = r.nextInt(this.len);
        } while ((p + read1_len) > this.len);
        sr1.qualLine.replace(0, qual1.length(), qual1);
        sr1.seqLine.replace(seq.substring(p, p + read1_len));
        sr1.mate_reverse_strand = false;
        sr1.query_reverse_strand = r.nextBoolean();
        sr1.pos = p + 1; //1 based
        sr1.mpos = -1;
        sr1.isize = -1;
    }

    public void PESample(SamRecord sr1,
            SamRecord sr2, String qual1, String qual2,
            int mean_ins, int stdev,
            int read1_len, int read2_len,
            String adapter1, String adapter2, Random r, InsertSizeDistribution insertSizeDistribution) {
        int p, l;
        if (mean_ins > this.len) {
            throw new RuntimeException("Fasta file has shorter sequence than the requested mean library length");
        }
        //increment the seq index
        sr1.seqIndex++;
        sr2.seqIndex++;

        do {
            p = r.nextInt(this.len);
            if (insertSizeDistribution != null) l = insertSizeDistribution.sample(r);
            else l = ((int) (r.nextGaussian() * ((double) stdev))) + mean_ins;
        } while ((p + Math.max(l, 0)) > this.len
                || (p + read1_len) > this.len
                || (p + Math.max(l, 0) - read2_len) < 0);
        if (l < 0) {
            l = 0; //don't let insert go under 0, 
            //but force all samplings less than 0 to 0
        }
        sr1.qualLine.replace(0, qual1.length(), qual1);
        sr2.qualLine.replace(0, qual2.length(), qual2);

        if (r.nextBoolean()) { //sample from forward strand
            sr1.seqLine.replace(seq.substring(p, p + read1_len));
            sr2.seqLine.replace(seq.substring(p + l - read2_len, p + l));
            //strand settings
            sr1.mate_reverse_strand = true;
            sr1.query_reverse_strand = false;
            sr1.pos = p + 1; //1 based
            sr1.mpos = p + l - read2_len + 1;
            sr1.isize = l;
            sr2.isize = -l;
            if (l < read1_len) {//add in adapter sequence
                //l is the position in the read to start adding in the adapter
                sr1.seqLine.replace(l, read1_len, adapter1.substring(0, read1_len - l));
            }
            if (l < read2_len) {//add in adapter sequence

                //this is going to get reversed.
                //the adapter comes in from the left:
                // ****|***-------|
                sr2.seqLine.reverseComplement();
                sr2.seqLine.replace(l, read2_len, adapter2.substring(0, read2_len - l));
                sr2.seqLine.reverseComplement();
            }
        } else {//sample from reverse strand
            sr1.seqLine.replace(seq.substring(p + l - read1_len, p + l));
            sr2.seqLine.replace(seq.substring(p, p + read2_len));
            //strand settings
            sr1.mate_reverse_strand = false;
            sr1.query_reverse_strand = true;
            sr1.pos = p + l - read1_len + 1; //1 based
            sr1.mpos = p + 1;
            sr1.isize = -l;
            sr2.isize = l;
            if (l < read2_len) {//add in adapter sequence
                //l is the position in the read to start adding in the adapter
                sr2.seqLine.replace(l, read2_len, adapter2.substring(0, read2_len - l));
            }
            if (l < read1_len) {//add in adapter sequence

                //this is going to get reversed.
                //the adapter comes in from the left:
                // ****|***-------|
                sr1.seqLine.reverseComplement();
                sr1.seqLine.replace(l, read1_len, adapter1.substring(0, read1_len - l));
                sr1.seqLine.reverseComplement();
            }
        }


        //things that can be inferred in 2 from 1
        sr2.mate_reverse_strand = sr1.query_reverse_strand;
        sr2.query_reverse_strand = sr1.mate_reverse_strand;
        sr2.pos = sr1.mpos;
        sr2.mpos = sr1.pos;
    }

    public void MPSample(SamRecord sr1,
            SamRecord sr2, String qual1, String qual2,
            int mate_ins, int mate_stdev, int read_ins, int read_stdev,
            int read1_len, int read2_len, double p_bad_pulldown, Random r) {
        int i, b, l, p;
        if (mate_ins > this.len) {
            throw new RuntimeException("Fasta file has shorter sequence than the requested mean library length");
        }
        //increment the seq index
        sr1.seqIndex++;
        sr2.seqIndex++;
        boolean shortMate = false;
        //grab our mate insert, our sheared mate loop, and position
        do {
            p = r.nextInt(this.len);
            l = ((int) (r.nextGaussian() * ((double) mate_stdev))) + mate_ins;
            i = ((int) (r.nextGaussian() * ((double) read_stdev))) + read_ins;
            b = r.nextInt(i); //the location of biotin
            if (b == 0 || b == i - 1 || p_bad_pulldown > r.nextDouble()) {
                shortMate = true;
                if (i >= read1_len && i >= read2_len && p + i <= this.len && i <= l) {
                    break;
                }
            } else {
                shortMate = false;
            }
        } while (i < read1_len
                || i < read2_len
                || (p + l) > this.len
                || l < read1_len
                || l < read2_len
                || i > l);


        //initialize sr1 and sr2
        sr1.qualLine.replace(0, qual1.length(), qual1);
        sr2.qualLine.replace(0, qual2.length(), qual2);
        sr1.chimeric = sr2.chimeric = false;
        sr1.mate_unmapped = sr2.mate_unmapped = false;
        sr1.query_unmapped = sr2.query_unmapped = false;
        sr1.proper_pair = sr2.proper_pair = true;

        boolean rev = r.nextBoolean();
        if (shortMate) {
            if (rev) { //set up flags for reverse mp library
                //   =====>2   1<======
                sr2.query_reverse_strand = sr1.mate_reverse_strand = false;
                sr1.query_reverse_strand = sr2.mate_reverse_strand = true;
            } else { // =====>1   2<======
                sr2.query_reverse_strand = sr1.mate_reverse_strand = true;
                sr1.query_reverse_strand = sr2.mate_reverse_strand = false;
            }
            //make surs CIGAR string is for all match (this could still be an SNP)
            sr1.cigar = Integer.toString(read1_len) + "M";
            sr2.cigar = Integer.toString(read2_len) + "M";
            if (rev) {//reverse strand sample
                sr2.seqLine.replace(seq.substring(p, p + read2_len));
                sr1.seqLine.replace(seq.substring(p + i - read1_len, p + i));
                sr1.pos = p + i - read1_len + 1; //1 based positions
                sr1.mpos = p + 1;
                sr1.isize = -i;
            } else {
                sr1.seqLine.replace(seq.substring(p, p + read1_len));
                sr2.seqLine.replace(seq.substring(p + i - read2_len, p + i));
                sr1.mpos = p + i - read2_len + 1; //1 based positions
                sr1.pos = p + 1;
                sr1.isize = i;
            }

            //infer sr2 things from sr1
            sr2.isize = -1 * sr1.isize;
            sr2.pos = sr1.mpos;
            sr2.mpos = sr1.pos;
        } else {
            if (rev) { //set up flags for reverse mp library
                //   <=====2   1======>
                sr2.query_reverse_strand = sr1.mate_reverse_strand = true;
                sr1.query_reverse_strand = sr2.mate_reverse_strand = false;
            } else { // <=====1   2======>
                sr2.query_reverse_strand = sr1.mate_reverse_strand = false;
                sr1.query_reverse_strand = sr2.mate_reverse_strand = true;
            }
            //two pieces, one of them starts at position
            //p+l-b and goes to either p+l-b+read length
            // if read length >= b, or goes to b and then
            // takes read length - b starting from p.
            // The other piece ends at p+i-b and starts at
            // the lesser of (i-b) or read length before that
            // if it (i-b) is less than read length, it takes
            // the difference up until p+l. Those two cases where
            // b happens within the read length lead to chimeric
            // reads.
            int rstart;
            if (rev) { //sample from reverse
                //both sequences are reversed, sr2 is taken from the p position
                // and sr1 from the p+l position.

                rstart = Math.abs(Math.min(0, i - b - read2_len));
                sr2.seqLine.replace(rstart, read2_len, seq.substring(p + i - b - read2_len + rstart, p + i - b));
                if (rstart != 0) {
                    sr2.pos = p + l - rstart + 1;
                    sr2.seqLine.replace(0, rstart, seq.substring(p + l - rstart, p + l));
                    sr2.cigar = Integer.toString(rstart)
                            + "M" + Integer.toString(read2_len - rstart) + "S";
                    sr2.c_cigar = Integer.toString(rstart)
                            + "M-" + Integer.toString(l - rstart - (i - b))
                            + "N" + Integer.toString(read2_len - rstart) + "M";
                    sr2.chimeric = true;
//                    sr2.query_unmapped = true;
//                    sr1.mate_unmapped = true;
//                    sr2.proper_pair=sr1.proper_pair=false;
//                    sr2.isize=sr1.isize=0;
                } else { //not a chimeric read, so cigar is all match
                    sr2.pos = p + i - b - read2_len + rstart + 1; //1 based
                    sr2.cigar = Integer.toString(read2_len) + "M";
                }
                rstart = Math.abs(Math.min(0, b - read1_len));
                sr1.seqLine.replace(0, read1_len - rstart, seq.substring(p + l - b, p + l - b + read1_len - rstart));
                sr1.pos = p + l - b + 1; //1 based
                if (rstart != 0) {
                    sr1.cigar = Integer.toString(read1_len - rstart)
                            + "M" + Integer.toString(rstart)
                            + "S";
                    sr1.c_cigar = Integer.toString(read1_len - rstart)
                            + "M-" + Integer.toString(l - b + read1_len - rstart)
                            + "N" + Integer.toString(rstart) + "M";
                    sr1.seqLine.replace(read1_len - rstart, read1_len, seq.substring(p, p + rstart));
                    sr1.chimeric = true;
//                    sr1.query_unmapped = true;
//                    sr2.mate_unmapped = true;
//                    sr2.proper_pair=sr1.proper_pair=false;
//                    sr1.isize=sr2.isize=0;
                } else { //not a chimeric read so CIGAR is all match
                    sr1.cigar = Integer.toString(read1_len) + "M";
                }
//                if(!sr1.chimeric && !sr2.chimeric){
//                    sr2.isize=l-read1_len;
//                    sr1.isize = - sr2.isize;
//                }
                sr1.mpos = sr2.pos;
                sr2.mpos = sr1.pos;
                int iSize = l - i;
                sr1.isize = -1 * iSize;
                sr2.isize = iSize;
            } else {//sample from forward
                rstart = Math.abs(Math.min(0, i - b - read1_len));
                sr1.seqLine.replace(rstart, read1_len, seq.substring(p + i - b - read1_len + rstart, p + i - b));
                if (rstart != 0) {
                    sr1.pos = p + l - rstart + 1;
                    sr1.seqLine.replace(0, rstart, seq.substring(p + l - rstart, p + l));
                    sr1.cigar = Integer.toString(rstart)
                            + "M" + Integer.toString(read1_len - rstart) + "S";
                    sr1.c_cigar = Integer.toString(rstart)
                            + "M-" + Integer.toString(l - rstart - (i - b))
                            + "N" + Integer.toString(read1_len - rstart) + "M";
                    sr1.chimeric = true;
//                    sr1.query_unmapped = true;
//                    sr2.mate_unmapped = true;
//                    sr1.proper_pair=sr2.proper_pair=false;
//                    sr1.isize=sr2.isize=0;
                } else { //not a chimeric read, so cigar is all match
                    sr1.pos = p + i - b - read1_len + rstart + 1; //1 based
                    sr1.cigar = Integer.toString(read1_len) + "M";
                }
                rstart = Math.abs(Math.min(0, b - read2_len));
                sr2.seqLine.replace(0, read2_len - rstart, seq.substring(p + l - b, p + l - b + read2_len - rstart));
                sr2.pos = p + l - b + 1; //1 based
                if (rstart != 0) {
                    sr2.cigar = Integer.toString(read2_len - rstart)
                            + "M" + Integer.toString(rstart)
                            + "S";
                    sr2.c_cigar = Integer.toString(read2_len - rstart)
                            + "M-" + Integer.toString(l - b + read2_len - rstart)
                            + "N" + Integer.toString(rstart) + "M";
                    sr2.seqLine.replace(read2_len - rstart, read2_len, seq.substring(p, p + rstart));
                    sr2.chimeric = true;
//                    sr2.query_unmapped = true;
//                    sr1.mate_unmapped = true;
//                    sr1.proper_pair=sr2.proper_pair=false;
//                    sr2.isize=sr1.isize=0;
                } else { //not a chimeric read so CIGAR is all match
                    sr2.cigar = Integer.toString(read2_len) + "M";
                }
//                if(!sr1.chimeric && !sr2.chimeric){
//                    sr1.isize=l-read2_len;
//                    sr2.isize = - sr1.isize;
//                }
                sr1.mpos = sr2.pos;
                sr2.mpos = sr1.pos;
                //in sam format the insert size is 5' to 5' end
                //thus for 
                int iSize = l - i;
                sr1.isize = iSize;
                sr2.isize = -1 * iSize;
            }
        }
    }
}
