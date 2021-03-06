package com.johnsnowlabs.nlp.annotators.parser.dep

import java.nio.file.{Files, Paths}

import com.johnsnowlabs.nlp._
import com.johnsnowlabs.nlp.annotators.Tokenizer
import com.johnsnowlabs.nlp.annotators.sbd.pragmatic.SentenceDetector
import com.johnsnowlabs.util.PipelineModels
import org.apache.spark.ml.Pipeline
import org.apache.spark.sql.{DataFrame, Dataset, Row}
import org.scalatest.FlatSpec
import SparkAccessor.spark.implicits._
import com.johnsnowlabs.nlp.training.POS
import com.johnsnowlabs.nlp.util.io.ResourceHelper
import com.johnsnowlabs.nlp.annotator.{PerceptronApproach, PerceptronModel}
import org.apache.spark.ml.util.MLWriter

import scala.language.reflectiveCalls

class DependencyParserModelTestSpec extends FlatSpec with DependencyParserBehaviors {

  private val documentAssembler = new DocumentAssembler()
    .setInputCol("text")
    .setOutputCol("document")

  private val sentenceDetector = new SentenceDetector()
    .setInputCols(Array("document"))
    .setOutputCol("sentence")

  private val tokenizer = new Tokenizer()
    .setInputCols(Array("sentence"))
    .setOutputCol("token")

  private val posTagger = getPerceptronModel

  private val dependencyParserTreeBank = new DependencyParserApproach()
    .setInputCols(Array("sentence", "pos", "token"))
    .setOutputCol("dependency")
    .setDependencyTreeBank("src/test/resources/parser/unlabeled/dependency_treebank")
    .setNumberOfIterations(10)

  private val dependencyParserConllU = new DependencyParserApproach()
    .setInputCols(Array("sentence", "pos", "token"))
    .setOutputCol("dependency")
    .setConllU("src/test/resources/parser/unlabeled/conll-u/train_small.conllu.txt")
    .setNumberOfIterations(10)

  private val pipelineTreeBank = new Pipeline()
    .setStages(Array(
      documentAssembler,
      sentenceDetector,
      tokenizer,
      posTagger,
      dependencyParserTreeBank
    ))

  private val pipelineConllU = new Pipeline()
    .setStages(Array(
      documentAssembler,
      sentenceDetector,
      tokenizer,
      posTagger,
      dependencyParserConllU
    ))

  private val emptyDataSet = PipelineModels.dummyDataset

  def getPerceptronModel: PerceptronModel = {
    val trainingPerceptronDF = POS().readDataset(ResourceHelper.spark, "src/test/resources/anc-pos-corpus-small/", "|", "tags")

    val perceptronTagger = new PerceptronApproach()
      .setInputCols(Array("token", "sentence"))
      .setOutputCol("pos")
      .setPosColumn("tags")
      .setNIterations(1)
      .fit(trainingPerceptronDF)
    val path = "./tmp_perceptrontagger"

    perceptronTagger.write.overwrite.save(path)
    val perceptronTaggerRead = PerceptronModel.read.load(path)
    perceptronTaggerRead
  }

  def trainDependencyParserModelTreeBank(): DependencyParserModel = {
    val model = pipelineTreeBank.fit(emptyDataSet)
    model.stages.last.asInstanceOf[DependencyParserModel]
  }

  def saveModel(model: MLWriter, modelFilePath: String): Unit = {
    model.overwrite().save(modelFilePath)
    assertResult(true){
      Files.exists(Paths.get(modelFilePath))
    }
  }

  "A Dependency Parser (trained through TreeBank format file)" should behave like {
    val testDataSet: Dataset[Row] =
      AnnotatorBuilder.withTreeBankDependencyParser(DataBuilder.basicDataBuild(ContentProvider.depSentence))
    initialAnnotations(testDataSet)
  }

  it should "save a trained model to local disk" in {
    val dependencyParserModel = trainDependencyParserModelTreeBank()
    saveModel(dependencyParserModel.write, "./tmp_dp_model")
  }

  it should "load a pre-trained model from disk" in {
    val dependencyParserModel = DependencyParserModel.read.load("./tmp_dp_model")
    assert(dependencyParserModel.isInstanceOf[DependencyParserModel])
  }

  "A dependency parser (trained through TreeBank format file) with an input text of one sentence" should behave like {

    val testDataSet = Seq("I saw a girl with a telescope").toDS.toDF("text")

    relationshipsBetweenWordsPredictor(testDataSet, pipelineTreeBank)
  }

  "A dependency parser (trained through TreeBank format file) with input text of two sentences" should
    behave like {

    val text = "I solved the problem with statistics. I saw a girl with a telescope"
    val testDataSet = Seq(text).toDS.toDF("text")

    relationshipsBetweenWordsPredictor(testDataSet, pipelineTreeBank)

  }

  "A dependency parser (trained through TreeBank format file) with an input text of several rows" should
   behave like {

    val text = Seq(
      "The most troublesome report may be the August merchandise trade deficit due out tomorrow",
      "Meanwhile, September housing starts, due Wednesday, are thought to have inched upward",
      "Book me the morning flight",
      "I solved the problem with statistics")
    val testDataSet = text.toDS.toDF("text")

    relationshipsBetweenWordsPredictor(testDataSet, pipelineTreeBank)
  }

  "A dependency parser (trained through Universal Dependencies format file) with an input text of one sentence" should
    behave like {

    val testDataSet = Seq("I saw a girl with a telescope").toDS.toDF("text")

    relationshipsBetweenWordsPredictor(testDataSet, pipelineConllU)
  }

  "A dependency parser (trained through Universal Dependencies format file) with input text of two sentences" should
    behave like {

    val text = "I solved the problem with statistics. I saw a girl with a telescope"
    val testDataSet = Seq(text).toDS.toDF("text")

    relationshipsBetweenWordsPredictor(testDataSet, pipelineConllU)

  }

  "A dependency parser (trained through Universal Dependencies format file) with an input text of several rows" should
    behave like {

    val text = Seq(
      "The most troublesome report may be the August merchandise trade deficit due out tomorrow",
      "Meanwhile, September housing starts, due Wednesday, are thought to have inched upward",
      "Book me the morning flight",
      "I solved the problem with statistics")
    val testDataSet = text.toDS.toDF("text")

    relationshipsBetweenWordsPredictor(testDataSet, pipelineConllU)
  }

  "A pre-trained dependency parser" should "find relationships between words" ignore {

    import com.johnsnowlabs.nlp.annotator.PerceptronModel

    val testDataSet = Seq("I saw a girl with a telescope").toDS.toDF("text")

    val documentAssembler = new DocumentAssembler()
      .setInputCol("text")
      .setOutputCol("document")

    val sentenceDetector = new SentenceDetector()
      .setInputCols(Array("document"))
      .setOutputCol("sentence")

    val tokenizer = new Tokenizer()
      .setInputCols(Array("sentence"))
      .setOutputCol("token")

    val posTagger = PerceptronModel.pretrained()
      .setInputCols("document", "token")
      .setOutputCol("pos")

    val dependencyParser = DependencyParserModel.pretrained()
      .setInputCols(Array("sentence", "pos", "token"))
      .setOutputCol("dependency")

    val pipeline = new Pipeline()
      .setStages(Array(
        documentAssembler,
        sentenceDetector,
        tokenizer,
        posTagger,
        dependencyParser
      ))

    val dependencyParserModel = pipeline.fit(emptyDataSet)

    val dependencyParserDataFrame = dependencyParserModel.transform(testDataSet)

    //dependencyParserDataFrame.show()

    assert(dependencyParserDataFrame.isInstanceOf[DataFrame])

  }

}
