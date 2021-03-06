package reader;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;

import edu.stanford.nlp.ling.CoreLabel;
import org.apache.commons.io.FileUtils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import structure.*;
import utils.FeatGen;
import utils.Params;
import utils.Tools;

public class Dataset {
	
	public static List<DataFormat> createDatasetFromOldFormat()
			throws Exception {
		String json = FileUtils.readFileToString(new File("data/questions.json"));
		List<DataFormat> oldProbs = new Gson().fromJson(json,
				new TypeToken<List<DataFormat>>(){}.getType());
		Map<Integer, List<Integer>> rateAnns = null;
//				Annotations.readRateAnnotations(Params.ratesFile);
		List<DataFormat> newProbs = new ArrayList<>();
		for(DataFormat kushmanProb : oldProbs) {
			DataFormat ks = new DataFormat();
			ks.iIndex = kushmanProb.iIndex;
			ks.sQuestion = kushmanProb.sQuestion;
			ks.lEquations = kushmanProb.lEquations;
			ks.lSolutions = kushmanProb.lSolutions;
			ks.lAlignments = kushmanProb.lAlignments;
			ks.quants = new ArrayList<>();
			for(QuantSpan qs : Tools.quantifier.getSpans(ks.sQuestion)) {
				ks.quants.add(qs.val);
			}
			ks.rates = new ArrayList<>();
			if(rateAnns.containsKey(ks.iIndex)) {
				ks.rates.addAll(rateAnns.get(ks.iIndex));
			}
			newProbs.add(ks);
		}
		return newProbs;
	}
	
	public static List<DataFormat> createDatasetFromCrowdFlower(String crowdFlowerFile)
			throws Exception {
		BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
		List<CrowdFlower> cfProblems = CrowdFlower.readCrowdFlowerFile(crowdFlowerFile);
		List<DataFormat> newProbs = new ArrayList<>();
		for(CrowdFlower prob : cfProblems) {
			for(int judgment = 0; judgment < prob.results.judgments.size(); ++judgment) {
				if(prob.results.judgments.get(judgment).tainted) continue;
				DataFormat ks = new DataFormat();
				ks.iIndex = prob.id;
				ks.sQuestion = prob.results.judgments.get(judgment).data.question1;
				ks.quants = new ArrayList<>();
				for (QuantSpan qs : Tools.quantifier.getSpans(ks.sQuestion)) {
					ks.quants.add(qs.val);
				}
				ks.lEquations = new ArrayList<>();
				String exp = prob.data.answer.split("=")[0].trim();
				String monotonic = covertExpressionToMonotonic(exp);
				//			if(!exp.trim().equals(monotonic.trim())) {
				//				System.out.println(exp + " converted to "+monotonic);
				//			}
				ks.lEquations.add(monotonic);
				ks.lSolutions = new ArrayList<>();
				ks.lSolutions.add(Double.parseDouble(prob.data.answer.split("=")[1].trim()));
				ks.rates = new ArrayList<>();
				ks.lAlignments = new ArrayList<>();
				Node expr = Node.parseNode(ks.lEquations.get(0));
				List<Node> leaves = expr.getLeaves();
				for (int j = 0; j < leaves.size(); ++j) {
					Node leaf = leaves.get(j);
					List<Integer> matchedQuantIndices = new ArrayList<>();
					for (int i = 0; i < ks.quants.size(); ++i) {
						if (Tools.safeEquals(ks.quants.get(i), leaf.val)) {
							matchedQuantIndices.add(i);
						}
					}
					if (matchedQuantIndices.size() == 0) {
						System.out.println(ks.iIndex + ": Quantity not found in " + leaf.val);
						System.out.println("Initial Problem: " + prob.data.question);
						System.out.println("Modified Problem: " + ks.sQuestion);
						System.out.println("WorkerId: " + prob.results.judgments.get(judgment).worker_id);
						System.out.println("Quantities: " + Arrays.asList(ks.quants));
						System.out.println("Answer: " + ks.lEquations.get(0) + " = " + ks.lSolutions.get(0));
						System.out.println();
					} else if (matchedQuantIndices.size() > 1) {
						System.out.println(ks.iIndex + ": More than 1 match found with " + leaf.val);
						System.out.println("Initial Problem: " + prob.data.question);
						System.out.println("Modified Problem: " + ks.sQuestion);
						System.out.println("WorkerId: " + prob.results.judgments.get(judgment).worker_id);
						System.out.println("Quantities: " + Arrays.asList(ks.quants));
						System.out.println("Answer: " + ks.lEquations.get(0) + " = " + ks.lSolutions.get(0));
						System.out.println();
					} else {
						ks.lAlignments.add(matchedQuantIndices.get(0));
					}
				}
				newProbs.add(ks);
			}
		}
		return newProbs;
	}

	public static String covertExpressionToMonotonic(String expression) {
		Node expr = Node.parseNode(expression);
		List<Node> nodes = expr.getAllSubNodes();
		Collections.reverse(nodes);
		for(Node node : nodes) {
			if (node.children.size() == 0) continue;
			if (node.children.get(0).label.equals("SUB")) {
				if (node.label.equals("ADD")) {
					node.label = "SUB";
					Node ab = node.children.get(0);
					Node c = node.children.get(1);
					node.children.clear();
					node.children.add(new Node("ADD", Arrays.asList(ab.children.get(0), c)));
					node.children.add(ab.children.get(1));
				} else if (node.label.equals("SUB")) {
					Node ab = node.children.get(0);
					Node c = node.children.get(1);
					node.children.clear();
					node.children.add(ab.children.get(0));
					node.children.add(new Node("ADD", Arrays.asList(ab.children.get(1), c)));
				}
			}
			if (node.children.get(1).label.equals("SUB")) {
				if (node.label.equals("ADD")) {
					node.label = "SUB";
					Node a = node.children.get(0);
					Node bc = node.children.get(1);
					node.children.clear();
					node.children.add(new Node("ADD", Arrays.asList(a, bc.children.get(0))));
					node.children.add(bc.children.get(1));
				} else if (node.label.equals("SUB")) {
					node.label = "SUB";
					Node a = node.children.get(0);
					Node bc = node.children.get(1);
					node.children.clear();
					node.children.add(new Node("ADD", Arrays.asList(a, bc.children.get(1))));
					node.children.add(bc.children.get(0));
				}
			}
			// For Mul, Div
			if (node.children.get(0).label.equals("DIV")) {
				if (node.label.equals("MUL")) {
					node.label = "DIV";
					Node ab = node.children.get(0);
					Node c = node.children.get(1);
					node.children.clear();
					node.children.add(new Node("MUL", Arrays.asList(ab.children.get(0), c)));
					node.children.add(ab.children.get(1));
				} else if (node.label.equals("DIV")) {
					Node ab = node.children.get(0);
					Node c = node.children.get(1);
					node.children.clear();
					node.children.add(ab.children.get(0));
					node.children.add(new Node("MUL", Arrays.asList(ab.children.get(1), c)));
				}
			}
			if (node.children.get(1).label.equals("DIV")) {
				if (node.label.equals("MUL")) {
					node.label = "DIV";
					Node a = node.children.get(0);
					Node bc = node.children.get(1);
					node.children.clear();
					node.children.add(new Node("MUL", Arrays.asList(a, bc.children.get(0))));
					node.children.add(bc.children.get(1));
				} else if (node.label.equals("DIV")) {
					node.label = "DIV";
					Node a = node.children.get(0);
					Node bc = node.children.get(1);
					node.children.clear();
					node.children.add(new Node("MUL", Arrays.asList(a, bc.children.get(1))));
					node.children.add(bc.children.get(0));
				}
			}
		}
		return expr.toString();
	}

	public static void makeExpressionEquation() throws IOException {
		String json = FileUtils.readFileToString(new File("data/questionsNew.json"));
		List<DataFormat> probs = new Gson().fromJson(json,
				new TypeToken<List<DataFormat>>(){}.getType());
		for(DataFormat prob : probs) {
			if(prob.lEquations.get(0).contains("=")) continue;
			prob.lEquations.add("X="+prob.lEquations.get(0));
			prob.lEquations.remove(0);
		}
		Gson gson = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
		json = gson.toJson(probs);
		FileUtils.writeStringToFile(new File("data/questions.json"), json);
	}

	public static void addRateAnnotations() throws IOException {
		String json = FileUtils.readFileToString(new File("data/questionsOld.json"));
		List<DataFormat> oldProbs = new Gson().fromJson(json,
				new TypeToken<List<DataFormat>>(){}.getType());
		json = FileUtils.readFileToString(new File("data/questions.json"));
		List<DataFormat> probs = new Gson().fromJson(json,
				new TypeToken<List<DataFormat>>(){}.getType());
		int count = 0;
		for(DataFormat prob : probs) {
			if(prob.lAlignments == null || prob.lAlignments.size() == 0) {
				for(DataFormat oldProb : oldProbs) {
					if(prob.iIndex == oldProb.iIndex) {
						System.out.println("Alignment added from "+prob.iIndex);
						prob.lAlignments = oldProb.lAlignments;
						count++;
						break;
					}
				}
			}
		}
		System.out.println("Changes made: "+count);
		Gson gson = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
		json = gson.toJson(probs);
		FileUtils.writeStringToFile(new File("data/questions.json"), json);
	}

	public static void combineTwoSetsToOneDataset() throws Exception {
		List<DataFormat> probs1 = createDatasetFromOldFormat();
		System.out.println("Probs1: "+probs1.size());
		List<DataFormat> probs2 = createDatasetFromCrowdFlower("data/job_1012604.json");
		System.out.println("Probs2: "+probs2.size());
		List<DataFormat> probs = new ArrayList<>();
		probs.addAll(probs1);
		probs.addAll(probs2);
		Gson gson = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
		String json = gson.toJson(probs);
		FileUtils.writeStringToFile(new File("data/questionsNew.json"), json);
	}

	public static void consistencyChecks() throws Exception {
		System.out.println("Running consistency checks");
		String json = FileUtils.readFileToString(new File("data/questions.json"));
		List<DataFormat> kushmanProbs = new Gson().fromJson(json,
				new TypeToken<List<DataFormat>>(){}.getType());
//		for(DataFormat prob : probs) {
//			Node expr = Node.parseNode(prob.lEquations.get(0).split("=")[1].trim());
//			if(expr.getLeaves().size() != prob.lAlignments.size()) {
//				System.out.println("Id: "+prob.iIndex);
//			}
//		}
		List<StanfordProblem> probs = Reader.readStanfordProblemsFromJson();
		for(int i=0; i<probs.size(); ++i) {
			StanfordProblem prob = probs.get(i);
			DataFormat df = kushmanProbs.get(i);
			List<Node> leaves = prob.expr.getLeaves();
			if(leaves.size() != df.lAlignments.size()) {
				System.out.println("Alignment size mismatch "+prob.id);
			}
			if(df.quants.size() != prob.quantities.size()) {
				System.out.println("Number list size mismatch "+prob.id);
			}
			if(df.quants.size() == prob.quantities.size()) {
				for(int j=0; j<prob.quantities.size(); ++j) {
					if(!Tools.safeEquals(prob.quantities.get(j).val, df.quants.get(j))) {
						System.out.println("Number not matching "+prob.id);
					}
				}
			}
			if(leaves.size() == df.lAlignments.size()) {
				for (int j = 0; j < leaves.size(); ++j) {
					if(!Tools.safeEquals(leaves.get(j).val,
							prob.quantities.get(df.lAlignments.get(j)).val)) {
						System.out.println("Alignment entry does not match number "+prob.id);
					}
				}
			}
			if(!Tools.safeEquals(prob.expr.getValue(), prob.answer)) {
				System.out.println("Answer not matching with expression "+prob.id);
			}
			for(Node node : prob.expr.getAllSubNodes()) {
				if(node.label.equals("MUL") || node.label.equals("DIV")) {
					if(prob.rates.size() == 0) {
						System.out.println("Rates absent "+prob.id);
						break;
					}
				}
			}
		}
		System.out.println("Problems read: "+probs.size());
	}

	public static void createFoldFiles() throws Exception {
		List<StanfordProblem> probs = Reader.readStanfordProblemsFromJson();
		List<Integer> indices = new ArrayList<>();
		List<Integer> oldIndices = new ArrayList<>();
		List<Integer> newIndices = new ArrayList<>();
		String oldP = "", newP = "";
		for(StanfordProblem prob : probs) {
			indices.add(prob.id);
			if(prob.id < 100000) {
				oldIndices.add(prob.id);
				oldP += prob.id + "\n";
			} else {
				newIndices.add(prob.id);
				newP += prob.id + "\n";
			}
		}
		Collections.shuffle(indices);
		double n = indices.size()*1.0 / 5;
		for(int i=0; i<5; ++i) {
			String str = "";
			int min = (int)(i*n);
			int max = (int)((i+1)*n);
			if(i == 4) max = indices.size();
			for(int j=min; j<max; ++j) {
				str += indices.get(j) + "\n";
			}
			FileUtils.writeStringToFile(new File("fold"+i+".txt"), str);
		}
		FileUtils.writeStringToFile(new File("old.txt"), oldP);
		FileUtils.writeStringToFile(new File("new.txt"), newP);
	}

	public static void computePMI(int startIndex, int endIndex) throws Exception {
		List<StanfordProblem> probs = Reader.readStanfordProblemsFromJson();
		int count = 0;
		Map<String, Integer> countsOp = new HashMap<>();
		Map<String, Integer> countsFeats = new HashMap<>();
		Map<String, Integer> countsJoint = new HashMap<>();
		for(StanfordProblem prob : probs) {
			if(prob.id > endIndex && endIndex != -1) {
				continue;
			}
			if(prob.id < startIndex && startIndex != -1) {
				continue;
			}
			count++;
			for(Node node : prob.expr.getAllSubNodes()) {
				Set<String> feats = new HashSet<>();
				if(node.children.size() > 0 &&
						node.children.get(0).children.size() == 0) {
					int sentId = Tools.getSentenceIdFromCharOffset(
							prob.tokens, node.children.get(0).qs.start);
					int tokenId = Tools.getTokenIdFromCharOffset(
							prob.tokens.get(sentId), node.children.get(0).qs.start);
					feats.addAll(FeatGen.getUnigramBigramFeatures(
							prob.tokens.get(sentId), tokenId, 3));
				}
				if(node.children.size() > 0 &&
						node.children.get(1).children.size() == 0) {
					int sentId = Tools.getSentenceIdFromCharOffset(
							prob.tokens, node.children.get(1).qs.start);
					int tokenId = Tools.getTokenIdFromCharOffset(
							prob.tokens.get(sentId), node.children.get(1).qs.start);
					feats.addAll(FeatGen.getUnigramBigramFeatures(
							prob.tokens.get(sentId), tokenId, 3));
				}
				if(feats.size() > 0) {
					countsOp.put(node.label, countsOp.getOrDefault(node.label, 0) + 1);
					for(String feat : feats) {
						countsFeats.put(feat, countsFeats.getOrDefault(feat, 0) + 1);
						countsJoint.put(node.label+"_"+feat,
								countsJoint.getOrDefault(node.label+"_"+feat, 0) + 1);
					}
				}
			}
		}
		System.out.println("Count: "+count);
		double aggregate = 0.0, aggEntropy = 0.0;
		for(String feat : countsFeats.keySet()) {
			int max = 0;
			for(String op : countsOp.keySet()) {
				if(countsJoint.getOrDefault(op+"_"+feat, 0) > max) {
					max = countsJoint.getOrDefault(op+"_"+feat, 0);
				}
			}
			aggregate += (max * 1.0 / countsFeats.get(feat));
			double entropy = 0.0;
			for(String op : countsOp.keySet()) {
				double p = countsJoint.getOrDefault(op+"_"+feat, 0) *1.0 / countsFeats.get(feat);
				if(p > 0.00001) {
					entropy += -p * Math.log(p);
				}
			}
			aggEntropy += entropy;
		}
		System.out.println("Average Best Choice Prob: "+
				(aggregate / countsFeats.keySet().size()));
		System.out.println("Average Entropy: "+
				(aggEntropy / countsFeats.keySet().size()));
	}

	public static void analyzeErrors(String fileName1, String fileName2, int total)
			throws IOException {
		List<String> lines1 = FileUtils.readLines(new File(fileName1));
		List<String> lines2 = FileUtils.readLines(new File(fileName2));
		Set<Integer> err1 = new HashSet<>();
		Set<Integer> err2 = new HashSet<>();
		Set<Integer> bothWrong = new HashSet<>();
		Set<Integer> RightWrong = new HashSet<>();
		Set<Integer> WrongRight = new HashSet<>();
		Map<Integer, String> probIdToString1 = new HashMap<>();
		Map<Integer, String> probIdToString2 = new HashMap<>();
		String bW = "", RW = "", WR = "";
		for(int j=0; j<lines1.size(); ++j) {
			String line = lines1.get(j);
			Integer i;
			try {
				i = Integer.parseInt(line.split(" : ")[0].trim());
			} catch (Exception e) {
				continue;
			}
			err1.add(i);
			String s = lines1.get(j) + "\n";
			do {
				j++;
				s += lines1.get(j) + "\n";
			} while(!lines1.get(j).startsWith("Pred"));

			probIdToString1.put(i, s);
		}
		for(int j=0; j<lines2.size(); ++j) {
			String line = lines2.get(j);
			Integer i;
			try {
				i = Integer.parseInt(line.split(" : ")[0].trim());
			} catch (Exception e) {
				continue;
			}
			err2.add(i);
			String s = lines2.get(j) + "\n";
			do {
				j++;
				s += lines2.get(j) + "\n";
			} while(!lines2.get(j).startsWith("Pred"));

			probIdToString2.put(i, s);
		}
		double b = 0, c = 0, union = err2.size(), intersection = 0;
		for(Integer i : err1) {
			if(!err2.contains(i)) {
				b++;
				WrongRight.add(i);
			}
			if(err2.contains(i)) {
				intersection += 1;
				bothWrong.add(i);
			}
			if(!err2.contains(i)) union += 1;
		}
		for(Integer i : err2) {
			if(!err1.contains(i)) {
				c++;
				RightWrong.add(i);
			}
		}
		System.out.println("Acc1: "+(1.0 - (err1.size()*1.0/total)));
		System.out.println("Acc2: "+(1.0 - (err2.size()*1.0/total)));
		System.out.println("Yes Yes : "+(total - union));
		System.out.println("Yes No : "+c);
		System.out.println("No Yes : "+b);
		System.out.println("No No : "+intersection);
//		System.out.println("RightWrong: "+Arrays.asList(RightWrong));
//		System.out.println("WrongRight: "+Arrays.asList(WrongRight));
//		System.out.println("bothWrong: "+Arrays.asList(bothWrong));
		String str = "";
		for(Integer i : RightWrong) {
			str += probIdToString2.get(i)+"\n";
		}
		FileUtils.writeStringToFile(new File("right_wrong.txt"), str);
		str = "";
		for(Integer i : WrongRight) {
			str += probIdToString1.get(i)+"\n";
		}
		FileUtils.writeStringToFile(new File("wrong_right.txt"), str);
		str = "";
		for(Integer i : bothWrong) {
			str += probIdToString2.get(i)+"\n";
		}
		FileUtils.writeStringToFile(new File("both_wrong.txt"), str);
	}

	public static void createDataForSimpleInterest(String rawProblemFile, String outputJsonFile)
			throws IOException {
		List<String> lines = FileUtils.readLines(new File(rawProblemFile));
		List<DataFormat> probs = new ArrayList<>();
		for(int i=0; i<lines.size(); i+=3) {
			String question = lines.get(i+2).trim();
			List<QuantSpan> quantities = Tools.quantifier.getSpans(question);
			Double ans = Tools.quantifier.getSpans(lines.get(i+1).trim()).get(0).val;
			List<Double> nums = new ArrayList<>();
			nums.add(ans);
			for(QuantSpan qs : quantities) {
				nums.add(qs.val);
			}
			boolean allow = false;
			int prod = -1;
			if(nums.size() == 4) {
				if(Tools.safeEquals(nums.get(0), nums.get(1)*nums.get(2)*nums.get(3))) {
					allow = true;
					prod = 0;
				}
				if(Tools.safeEquals(nums.get(1), nums.get(0)*nums.get(2)*nums.get(3))) {
					allow = true;
					prod = 1;
				}
				if(Tools.safeEquals(nums.get(2), nums.get(1)*nums.get(0)*nums.get(3))) {
					allow = true;
					prod = 2;
				}
				if(Tools.safeEquals(nums.get(3), nums.get(1)*nums.get(2)*nums.get(0))) {
					allow = true;
					prod = 3;
				}
			}
			if(!allow) {
				System.out.println("Problem: "+question);
				System.out.println("Extracted answer: "+ans);
			}
			if(allow) {
				DataFormat df = new DataFormat();
				df.sQuestion = question;
				df.lSolutions = new ArrayList<>();
				df.lSolutions.add(ans);
				df.quants = new ArrayList<>();
				for(QuantSpan qs : quantities) {
					df.quants.add(qs.val);
				}
				df.iIndex = 110000+(i/3);
				df.rates = new ArrayList<>();
				df.lAlignments = new ArrayList<>();

				// First find the right equation
				String eq = "X=";
				if(prod == 0) {
					eq += "("+nums.get(1)+"*("+nums.get(2)+"*"+nums.get(3)+"))";
					df.lAlignments.addAll(Arrays.asList(0,1,2));
				}
				if(prod == 1) {
					eq += "("+nums.get(1)+"/("+nums.get(2)+"*"+nums.get(3)+"))";
					df.lAlignments.addAll(Arrays.asList(0,1,2));
				}
				if(prod == 2) {
					eq += "("+nums.get(2)+"/("+nums.get(1)+"*"+nums.get(3)+"))";
					df.lAlignments.addAll(Arrays.asList(1,0,2));
				}
				if(prod == 3) {
					eq += "("+nums.get(3)+"/("+nums.get(1)+"*"+nums.get(2)+"))";
					df.lAlignments.addAll(Arrays.asList(2,0,1));
				}
				df.lEquations = new ArrayList<>();
				df.lEquations.add(eq);
				probs.add(df);
			}
		}
		Gson gson = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
		String json = gson.toJson(probs);
		FileUtils.writeStringToFile(new File(outputJsonFile), json);


	}

	public static void findLexicallyVariedSubset(List<StanfordProblem> problems) {
		boolean flag;
		List<Integer> ids = new ArrayList<>();
		for(int i=0; i<problems.size(); ++i) {
			StanfordProblem prob = problems.get(i);
			flag = false;
			for(int j=0; j<i; ++j) {
				double sim = Tools.getLexicalSimilarity(prob.tokens, problems.get(j).tokens);
//				System.out.println(prob.id+" "+problems.get(j).id+" "+sim);
				if(sim > 0.9) {
					flag = true;
					break;
				}
			}
			if(!flag) ids.add(prob.id);
		}
		System.out.println(ids.size());
		System.out.println(ids);
	}

	public static void main(String args[]) throws Exception {
		Tools.initStanfordTools();
//		consistencyChecks();
//		createFoldFiles();
//		computePMI(0, 10000);
//		computePMI(10000, -1);
//		computePMI(-1, -1);
//		analyzeErrors(args[0], args[1], Integer.parseInt(args[2]));
//		createDataForSimpleInterest("si.txt", "si.json");
		Params.questionsFile = "data/simple_interest/si.json";
		List<StanfordProblem> probs = Reader.readStanfordProblemsFromJson();
		findLexicallyVariedSubset(probs);
//		for(StanfordProblem prob : probs) {
//			if(!Tools.safeEquals(prob.expr.getValue(), prob.answer)) {
//				System.out.println("Problem in "+prob.id);
//			}
//		}

	}

 	
}
