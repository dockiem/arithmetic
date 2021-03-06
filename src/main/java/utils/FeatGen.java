package utils;

import java.util.ArrayList;
import java.util.List;

import edu.illinois.cs.cogcomp.core.datastructures.Pair;
import edu.illinois.cs.cogcomp.core.datastructures.textannotation.Constituent;
import edu.illinois.cs.cogcomp.core.datastructures.textannotation.TextAnnotation;
import edu.illinois.cs.cogcomp.sl.util.FeatureVectorBuffer;
import edu.illinois.cs.cogcomp.sl.util.IFeatureVector;
import edu.illinois.cs.cogcomp.sl.util.Lexiconer;
import edu.stanford.nlp.ling.CoreLabel;

public class FeatGen {
	
	public static IFeatureVector getFeatureVectorFromListString(
			List<String> features, Lexiconer lm) {
		FeatureVectorBuffer fvb = new FeatureVectorBuffer();
		for(String feature : features) {
			if(!lm.containFeature(feature) && lm.isAllowNewFeatures()) {
				lm.addFeature(feature);
			}
			if(lm.containFeature(feature)) {
				fvb.addFeature(lm.getFeatureId(feature), 1.0);
			}
		}
		return fvb.toFeatureVector();
	}
	
	public static IFeatureVector getFeatureVectorFromListPair(
			List<Pair<String, Double>> features, Lexiconer lm) {
		FeatureVectorBuffer fvb = new FeatureVectorBuffer();
		for(Pair<String, Double> feature : features) {
			if(!lm.containFeature(feature.getFirst()) && lm.isAllowNewFeatures()) {
				lm.addFeature(feature.getFirst());
			}
			if(lm.containFeature(feature.getFirst())) {
				fvb.addFeature(lm.getFeatureId(feature.getFirst()), feature.getSecond());
			}
		}
		return fvb.toFeatureVector();
	}
		
	public static List<String> getConjunctions(List<String> features) {
		List<String> conjunctions = new ArrayList<String>();
		for(int i=0; i<features.size(); ++i) {
			for(int j=i+1; j<features.size(); ++j) {
				conjunctions.add(features.get(i)+"_"+features.get(j));
			}
		}
		return conjunctions;
	}
	
	public static List<String> getConjunctions(
			List<String> features1, List<String> features2) {
		List<String> conjunctions = new ArrayList<String>();
		for(String feature1 : features1) {
			for(String feature2 : features2) {
				conjunctions.add(feature1+"_"+feature2);
			}
		}
		return conjunctions;
	}
	
	public static List<Pair<String, Double>> getConjunctionsWithPairs(
			List<Pair<String, Double>> features1, List<Pair<String, Double>> features2) {
		List<Pair<String, Double>> conjunctions = new ArrayList<Pair<String, Double>>();
		for(Pair<String, Double> feature1 : features1) {
			for(Pair<String, Double> feature2 : features2) {
				conjunctions.add(new Pair<String, Double>(
						feature1.getFirst()+"_"+feature2.getFirst(), 
						feature1.getSecond()*feature2.getSecond()));
			}
		}
		return conjunctions;
	}
	
	public static List<String> getUnigramBigramFeatures(
			TextAnnotation ta, List<Constituent> posTags, int start, int end) {
		List<String> feats = new ArrayList<>();
		List<String> tokens = new ArrayList<>();
		for(int i=start; i<end; ++i) {
			if(posTags.get(i).getLabel().startsWith("N")) {
				tokens.add("N");
			} else if(posTags.get(i).getLabel().startsWith("CD")) {
				tokens.add("CD");
			} else {
				tokens.add(ta.getToken(i));
			}
		}
		for(String tkn : tokens) {
			feats.add("Unigram_"+tkn);
		}
		for(int i=0; i<tokens.size()-1; ++i) {
			feats.add("Bigram_"+tokens.get(i)+"_"+tokens.get(i+1));
		}
		return feats;
	}

	public static List<String> getUnigramBigramFeatures(
			List<CoreLabel> tokens, int index, int window) {
		List<String> feats = new ArrayList<>();
		List<String> lemmas = new ArrayList<>();
		int start = Math.max(0, index-window);
		int end = Math.min(tokens.size()-1, index+window);
		for(int i=start; i<=end; ++i) {
//			if(tokens.get(i).tag().startsWith("N")) {
//				lemmas.add("N");
//			} else
			if(tokens.get(i).tag().startsWith("CD")) {
				lemmas.add("CD");
			} else {
				lemmas.add(tokens.get(i).lemma());
			}
		}
		for(String tkn : lemmas) {
			feats.add("Unigram_"+tkn);
		}
		for(int i=0; i<lemmas.size()-1; ++i) {
			feats.add("Bigram_"+lemmas.get(i)+"_"+lemmas.get(i+1));
		}
		return feats;
	}

	public static List<String> getUnigramBigramFeatures(List<CoreLabel> tokens) {
		List<String> feats = new ArrayList<>();
		List<String> lemmas = new ArrayList<>();
		for(int i=0; i<tokens.size(); ++i) {
			if(tokens.get(i).tag().startsWith("CD")) {
				lemmas.add("CD");
			} else {
				lemmas.add(tokens.get(i).lemma());
			}
		}
		for(String tkn : lemmas) {
			feats.add("Unigram_"+tkn);
		}
		for(int i=0; i<lemmas.size()-1; ++i) {
			feats.add("Bigram_"+lemmas.get(i)+"_"+lemmas.get(i+1));
		}
		return feats;
	}
	
	public static List<String> getNeighborhoodFeatures(
			TextAnnotation ta, List<Constituent> posTags, int index, int window) {
		List<String> feats = new ArrayList<>();
		for(int i=Math.max(0, index-window); i<Math.min(ta.size()-1, index+window); ++i) {
			feats.add("Context_"+ta.getToken(i).toLowerCase()+"_"+posTags.get(i+1).getLabel());
			feats.add("Context_"+posTags.get(i).getLabel()+"_"+ta.getToken(i+1).toLowerCase());
		}
		return feats;
	}

	public static List<String> getNeighborhoodFeatures(List<CoreLabel> tokens, int index, int window) {
		List<String> feats = new ArrayList<>();
		for(int i=Math.max(0, index-window); i<Math.min(tokens.size()-1, index+window); ++i) {
			feats.add("Context_"+tokens.get(i).lemma()+"_"+tokens.get(i+1).tag());
			feats.add("Context_"+tokens.get(i).tag()+"_"+tokens.get(i).lemma());
		}
		return feats;
	}

	public static List<String> getFeaturesConjWithLabels(List<String> feats, String label) {
		List<String> f =new ArrayList<>();
		for(String feat : feats) {
			f.add(label+"_"+feat);
		}
		return f;
	}
	
}
