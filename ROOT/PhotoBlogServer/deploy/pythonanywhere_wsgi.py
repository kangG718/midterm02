"""Copy this file into the WSGI configuration linked from PythonAnywhere's Web tab."""
import os
import sys

project_path = "/home/garamkang/photoblog-assessment/ROOT/PhotoBlogServer"
if project_path not in sys.path:
    sys.path.insert(0, project_path)

os.environ["DJANGO_SETTINGS_MODULE"] = "mysite.settings"
os.environ["DJANGO_DEBUG"] = "False"

from django.core.wsgi import get_wsgi_application

application = get_wsgi_application()
