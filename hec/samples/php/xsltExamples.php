<!DOCTYPE html SYSTEM "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<html xmlns="http://www.w3.org/1999/xhtml">
    <head>
        <title>Saxon/C API design use cases</title>
    </head>
    <body>
        <?php 
            
            /* simple example to show transforming to string */
            function exampleSimple1($proc, $xmlfile, $xslFile){
                $proc->setSourceFromFile($xmlfile);
                $proc->compileFromFile($xslFile);
  	              
                $result = $proc->transformToString();               
		if($result != null) {               
		echo '<b>exampleSimple1:</b><br/>';		
		echo 'Output:'.$result;
		} else {
			echo "Result is null";
		}
		$proc->clearParameters();
		$proc->clearProperties();            
            }
            
            /* simple example to show transforming to file */
            function exampleSimple2($proc, $xmlFile, $xslFile){
		echo '<b>exampleSimple2:</b><br/>';
                $proc->setSourceFromFile($xmlFile);
                $proc->compileFromFile($xslFile);
                $filename = "/home/ond1/temp/output1.xml";
		$proc->setOutputFile($filename);
                $proc->transformToFile();
				
		if (file_exists($filename)) {
		    echo "The file $filename exists";
		    unlink($filename);
		} else {
		    echo "The file $filename does not exist";
		}
       		$proc->clearParameters();
		$proc->clearProperties();
            }
            /* simple example to show importing a document as string and stylesheet as a string */
            function exampleSimple3($saxonProc, $proc){
		echo '<b>exampleSimple3:</b><br/>';
		$proc->clearParameters();
		$proc->clearProperties();
                $xdmNode = $saxonProc->parseXmlFromString("<doc><b>text value of out</b></doc>");
		if($xdmNode == null) {
			echo 'xdmNode is null';
			return;	
		}            
		$proc->setSourceFromXdmValue($xdmNode);
                $proc->compileFromString("<xsl:stylesheet xmlns:xsl='http://www.w3.org/1999/XSL/Transform' version='2.0'>
					    	<xsl:template match='/'>
					    	    <xsl:copy-of select='.'/>
					    	</xsl:template>
					    </xsl:stylesheet>");

                $result = $proc->transformToString();
		echo '<b>exampleSimple3</b>:<br/>';
		if($result != null) {             		
		echo 'Output:'.$result;
		} else {
			echo "Result is null";
		}       
			 
		$proc->clearParameters();
		$proc->clearProperties(); 
	         unset($xdmNode); 
            }

		function exampleLoopVar($saxonProc, $proc, $xml, $xslt){
$params = array(
  "testparam1" => "testvalue1",
  "testparam2" => "testvalue2",
  "testparam3" => "testvalue3",
);
echo '<b>exampleLoopVar:</b><br/>';

$proc->setSourceFromFile($xml);
$proc->compileFromFile($xslt);
$values = array();
foreach($params as $name => $value) {
  $xdmValue = $saxonProc->createAtomicValue(strval($value));
if($xdmValue !=null) {
   // echo "Name of Class " , get_class($xdmValue) , "\n";
    $proc->setParameter($name, $xdmValue);
  } else {
    echo "Xdmvalue is null";
  }
	//unset($xdmValue);
}
echo "Before transformToString";
$result = $proc->transformToString();
if($result != null) {
  echo 'Output:'.$result;
} else {
  echo "Result is null";
}

$proc->clearParameters();
$proc->clearProperties();
		}


           
           
            function exampleParam($saxonProc, $proc, $xmlFile, $xslFile){
                echo "\n",'<b>ExampleParam:</b><br/>';

                $proc->setSourceFromFile($xmlFile);
                $proc->compileFromFile($xslFile);
		$xdmvalue = $saxonProc->createAtomicValue(strval("Hello to you"));
		if($xdmvalue !=null){
			echo "Name of Class " , get_class($xdmvalue) , "\n"; 			
			$str = $xdmvalue->getStringValue();
			if($str!=null) {
				echo "XdmValue:".$str;
			}
			$proc->setParameter('a-param', $xdmvalue);
		} else {
			echo "Xdmvalue is null";
		}
                $result = $proc->transformToString();
		if($result != null) {                
			echo 'Output:'.$result."<br/>";
		} else {
			echo "Result is NULL<br/>";
		}
               
                $proc->clearParameters();                
                //unset($result);
                echo 'again with a no parameter value<br/>';
		$proc->setProperty('!indent', 'yes'); //Serialization property indicated with a '!' symbol
                $result = $proc->transformToString();
               
                $proc->clearProperties();
                echo $result;
                echo '<br/>';
              //  unset($result);
                echo 'again with no parameter and no properties value set. This should fail as no contextItem set<br/>';
                $xdmvalue = $saxonProc->createAtomicValue(strval("goodbye to you"));
		$proc->setParameter('a-param', $xdmvalue);
		
                $result = $proc->transformToString();                
                echo $result;
		$proc->clearParameters();
		$proc->clearProperties(); 
                        
            }


            function exampleXMLFilterChain($proc, $xmlFile, $xsl1File, $xsl2File, $xsl3File){
                echo '<b>XML Filter Chain using setSource</b><br/>';                
                $proc->setSourceFromFile($xmlFile);
                $proc->compileFromFile($xsl1File);
                $xdmValue1 = $proc->transformToValue();
                
                $proc->compileFromFile($xsl2File);
                $proc->setSourceFromXdmValue($xdmValue1);
                unset($xdmValue1);
                $xdmValue1 = $proc->transformToValue();
                
                $proc->compileFromFile($xsl3File);                
                $proc->setSourceFromXdmValue($xdmValue1);
                $result = $proc->transformToString();
		if($result != null) {
                	echo 'Output:'.$result;        
		} else {
			echo 'Result is null';
				    $errCount = $proc->getExceptionCount();
				    if($errCount > 0 ){ 
				        for($i = 0; $i < $errCount; $i++) {
					       $errCode = $proc->getErrorCode(intval($i));
					       $errMessage = $proc->getErrorMessage(intval($i));
					       echo 'Expected error: Code='.$errCode.' Message='.$errMessage;
					   }
						$proc->exceptionClear();	
					}
		}                      
		$proc->clearParameters();
		$proc->clearProperties();
            }
            
            function exampleXMLFilterChain2($saxonProc, $proc, $xmlFile, $xsl1File, $xsl2File, $xsl3File){
                echo '<b>XML Filter Chain using Parameters</b><br/>';                
                $xdmNode = $saxonProc->parseXmlFromFile($xmlFile);
		if($xdmNode == NULL) {
			echo 'source node is NULL';
		}
                $proc->setParameter('node', $xdmNode);
                $proc->compileFromFile($xsl1File);

                $xdmValue1 = $proc->transformToValue();
 		$errCount = $proc->getExceptionCount();
		if($errCount > 0 ){ 
			for($i = 0; $i < $errCount; $i++) {
			       $errCode = $proc->getErrorCode(intval($i));
			       $errMessage = $proc->getErrorMessage(intval($i));
			       echo 'Expected error: Code='.$errCode.' Message='.$errMessage;
					   }
			$proc->exceptionClear();	
		}
                $proc->clearParameters();
               
                $proc->compileFromFile($xsl2File);
                $proc->setSourceFromXdmValue($xdmValue1);
                $xdmValue1 = $proc->transformToValue();
		$errCount = $proc->getExceptionCount();
		if($errCount > 0 ){ 
			for($i = 0; $i < $errCount; $i++) {
			       $errCode = $proc->getErrorCode(intval($i));
			       $errMessage = $proc->getErrorMessage(intval($i));
			       echo 'Expected error: Code='.$errCode.' Message='.$errMessage;
					   }
			$proc->exceptionClear();	
		}
                $proc->clearParameters();
                
                $proc->compileFromFile($xsl3File);                
                $proc->setParameter('node', $xdmValue1);
                $result = $proc->transformToString();
		if($result != null) {
                	echo 'Output:'.$result;        
		} else {
			echo 'Result is null';
				    $errCount = $proc->getExceptionCount();
				    if($errCount > 0 ){ 
				        for($i = 0; $i < $errCount; $i++) {
					       $errCode = $proc->getErrorCode(intval($i));
					       $errMessage = $proc->getErrorMessage(intval($i));
					       echo 'Expected error: Code='.$errCode.' Message='.$errMessage;
					   }
						$proc->exceptionClear();	
					}
		}        
		$proc->clearParameters();
		$proc->clearProperties();
            }            

            /* simple example to detect and handle errors from a transformation */
            function exampleError1($proc, $xmlFile, $xslFile){
		echo '<br/><b>exampleError1:</b><br/>';
                $proc->setSourceFromFile($xmlFile);
                $proc->compileFromFile($xslFile);
                
                $result = $proc->transformToString();
                
                if($result == NULL) {
                    $errCount = $proc->getExceptionCount();
				    if($errCount > 0 ){ 
				        for($i = 0; $i < $errCount; $i++) {
					       $errCode = $proc->getErrorCode(intval($i));
					       $errMessage = $proc->getErrorMessage(intval($i));
					       echo 'Expected error: Code='.$errCode.' Message='.$errMessage;
					   }
						$proc->exceptionClear();	
					} else {
						echo '<b>Error not reported correctly</b>';
					}
                
                
                }                
                echo $result;
            	$proc->clearParameters();
		$proc->clearProperties();
            
            }   


            /* simple example to test transforming without an stylesheet */
            function exampleError2($proc, $xmlFile, $xslFile){
		echo '<b>exampleError2:</b><br/>';
                $proc->setSourceFromFile($xmlFile);
                $proc->compileFromFile($xslFile);
                
                $result = $proc->transformToString();
                
                if($result == NULL) {
                    $errCount = $proc->getExceptionCount();
				    if($errCount > 0 ){ 
				        for($i = 0; $i < $errCount; $i++) {
					       $errCode = $proc->getErrorCode(intval($i));
					       $errMessage = $proc->getErrorMessage(intval($i));
					       echo 'Expected error: Code='.$errCode.' Message='.$errMessage;
					   }
						$proc->exceptionClear();	
					}
                
                
                }                
                echo $result;            
           	$proc->clearParameters();
		$proc->clearProperties();
            
            }   
            
            
            $foo_xml = "xml/foo.xml";
            $foo_xsl = "xsl/foo.xsl";
            $baz_xml = "xml/baz.xml";
            $baz_xsl = "xsl/baz.xsl";
            $foo2_xsl = "xsl/foo2.xsl";
            $foo3_xsl = "xsl/foo3.xsl";
            $err_xsl = "xsl/err.xsl";            
            $err1_xsl = "xsl/err1.xsl";
            $text_xsl = "xsl/text.xsl";
            $cities_xml = "xml/cities.xml";
            $embedded_xml = "xml/embedded.xml";
            $multidoc_xsl = "xsl/multidoc.xsl";
            $identity_xsl = "xsl/identity.xsl"; 
            
            $saxonProc = new Saxon\SaxonProcessor();
	    $proc = $saxonProc->newXsltProcessor();	
            $version = $saxonProc->version();
            echo 'Saxon Processor version: '.$version;
            echo '<br/>';        
            exampleSimple1($proc, $foo_xml, $foo_xsl);
            echo '<br/>';
            exampleSimple2($proc, "xml/foo.xml", $foo_xsl);
            echo '<br/>';            
            exampleSimple3($saxonProc, $proc);
            echo '<br/>';
	    exampleLoopVar($saxonProc, $proc, $foo_xml, $foo_xsl);
            exampleParam($saxonProc, $proc, $foo_xml, $foo_xsl);
            exampleError1($proc, $foo_xml, $err_xsl);
            echo '<br/>'; 
	    exampleError2($proc, $foo_xml, $err1_xsl);
            echo '<br/>';
            exampleXMLFilterChain($proc, $foo_xml, $foo_xsl, $foo2_xsl, $foo3_xsl);
            echo '<br/>';                    
            exampleXMLFilterChain2($saxonProc, $proc, $foo_xml, $foo_xsl, $foo2_xsl, $foo3_xsl);          
            echo '<br/>';  

            
            unset($proc);
	    unset($saxonproc);
	
        
        ?>
    </body>
</html>
