import os 
import pytest
import sys
root = os.path.dirname(os.path.abspath(__file__))

def rec_dir(path, times):
    if times > 0:
        path = rec_dir(os.path.split(path)[0], times-1)
    return path

python_dir = rec_dir(root,3)
src_code_utils = os.path.join(python_dir, 'main', 'vidyavaani', 'utils')
sys.path.insert(0, src_code_utils)
from find_files import findFiles

dir_path = os.path.join(rec_dir(root,1), 'test_resources', 'find_files')

def test_good_find_files():
	# directory is string and it exists
    # dir_path = os.path.dirname(os.path.realpath(__file__))
    data_path = dir_path
    # files is a list
    files = ['.csv']
    result = findFiles(data_path,files)
    assert result == [os.path.join(data_path, 'vectors.csv'), os.path.join(data_path, 'vectors_after.csv')]


def test_bad_find_files_dir_not_exist():
	# directory is string but does not exists
    # dir_path = os.path.dirname(os.path.realpath(__file__))
    data_path = os.path.join(dir_path, 'Data23425')
    # files is a list
    files = ['.csv']

    assert(findFiles(data_path,files) == [])

def test_bad_find_files_substring_not_list():
	# directory is string but does not exists
    # dir_path = os.path.dirname(os.path.realpath(__file__))
    data_path = os.path.join(dir_path, 'Data')
    # files is a list
    files = '.csv'

    assert(findFiles(data_path,files) == [])