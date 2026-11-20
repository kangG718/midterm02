"""Copy this file into the WSGI configuration linked from PythonAnywhere's Web tab."""
import os
import sys

# Set DJANGO_PROJECT_PATH to the absolute PhotoBlogServer directory in the web app environment.
project_path = os.environ["DJANGO_PROJECT_PATH"]
if project_path not in sys.path:
    sys.path.insert(0, project_path)

os.environ["DJANGO_SETTINGS_MODULE"] = "mysite.settings"
os.environ["DJANGO_DEBUG"] = "False"

from django.core.wsgi import get_wsgi_application

application = get_wsgi_application()
