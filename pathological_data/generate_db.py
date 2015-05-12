#!/usr/bin/env python
import sqlite3
import os
import sys
import binascii
import glob

def get_basedir():
    return os.path.dirname(os.path.realpath(__file__))

def get_abs_filename(*filename):
    return os.path.join(get_basedir(), *filename)

def readfile(filename):
    content = ""
    if os.path.exists(filename) and os.path.isfile(filename):
        with open(filename, "rb") as content_file:
            content = content_file.read()
    return content

database = get_abs_filename("..", "app", "src", "main", "assets", "pathological.db")
print database

if os.path.exists(database):
    os.remove(database)

conn = sqlite3.connect(database)
c = conn.cursor()

c.execute("""
CREATE TABLE "pathology" (
    "id" INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    "derivation" TEXT NOT NULL,
    "description" TEXT NOT NULL,
    "description_es" TEXT,
    "description_pt" TEXT,
    "image" BLOB NOT NULL
);
""")

id_ = 0
for derivation in ['aVF', 'aVL', 'aVR', 
                   'DI', 'DII', 'DIII', 
                   'V1',  'V2', 'V3', 'V4', 'V5', 'V6']:
    wildcard = get_abs_filename(derivation, "*.png")

    for filename in glob.glob(wildcard):
        basename = os.path.basename(filename).rstrip(".png")
        file_png = filename
        file_description = get_abs_filename(derivation, ".".join([basename, "txt"]))
        file_description_es = get_abs_filename(derivation, ".".join([basename, "es", "txt"]))
        file_description_pt = get_abs_filename(derivation, ".".join([basename, "pt", "txt"]))
        id_ = id_ + 1 
        description = readfile(file_description).rstrip()
        c.execute("""INSERT INTO pathology VALUES (?, ?, ?, ?, ?, ?);""",
            (id_, 
            derivation, 
            description, 
            readfile(file_description_es).rstrip() or description, 
            readfile(file_description_pt).rstrip() or description,
            buffer(readfile(file_png))))
    
conn.commit()
conn.close()

