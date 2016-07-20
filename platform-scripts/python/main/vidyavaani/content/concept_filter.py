# Author: Aditya Arora, adityaarora@ekstepplus.org

import requests
import codecs
import os
import json
#Pass as a commandline argument later on
root=os.path.dirname(os.path.abspath(__file__))
utils=os.path.join(os.path.split(root)[0],'utils')
import sys
sys.path.insert(0, utils)#Insert at front of list ensuring that our util is executed first in from getAllValues import *
from get_all_values import getAllValues

#This finds the concepts from the assessment data
def filter_assessment_data(directory,conceptList):
	concList=set([])
	with codecs.open(os.path.join(directory,'items.json'),'r',encoding='utf-8') as f:
		item_list=json.load(f)
	f.close()
	for key in item_list.keys():
		vals=getAllValues(item_list[key])	
		for item in vals:
			if(item in conceptList):
				concList.add(item)
	with codecs.open(os.path.join(directory,'concepts.txt'),'w',encoding='utf-8') as f:
		f.write(','.join(concList))
	f.close()
	return concList
