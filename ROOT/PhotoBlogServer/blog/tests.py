"""Automated checks for the photo blog REST API."""

from io import BytesIO
from tempfile import TemporaryDirectory

from django.contrib.auth import get_user_model
from django.core.files.uploadedfile import SimpleUploadedFile
from django.test import TestCase
from django.test.utils import override_settings
from django.urls import reverse
from django.utils import timezone
from PIL import Image
from rest_framework.test import APIClient

from .models import Post


class PostAPITests(TestCase):
    def setUp(self):
        self.user = get_user_model().objects.create_user(username="api_tester")
        self.client = APIClient()
        self.post_url = reverse("post-list")
        self.post = Post.objects.create(
            author=self.user,
            title="첫 번째 사진",
            text="사진 설명",
            published_date=timezone.now(),
        )

    def test_get_post_list_returns_json_array(self):
        response = self.client.get(self.post_url, HTTP_ACCEPT="application/json")

        self.assertEqual(response.status_code, 200)
        self.assertEqual(response["Content-Type"], "application/json")
        self.assertIsInstance(response.data, list)
        self.assertEqual(len(response.data), 1)
        self.assertEqual(response.data[0]["author"], self.user.pk)
        self.assertEqual(response.data[0]["title"], self.post.title)
        self.assertEqual(response.data[0]["text"], self.post.text)
        self.assertIn("image", response.data[0])

    def test_get_post_detail_returns_requested_post(self):
        detail_url = reverse("post-detail", args=[self.post.pk])
        response = self.client.get(detail_url, HTTP_ACCEPT="application/json")

        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.data["title"], self.post.title)

    def test_anonymous_user_cannot_create_post(self):
        post_data = {"author": self.user.pk, "title": "차단 대상", "text": "설명"}
        response = self.client.post(self.post_url, post_data, format="json")

        self.assertIn(response.status_code, (401, 403))
        self.assertEqual(Post.objects.count(), 1)

    def test_authenticated_user_can_upload_post_with_image(self):
        self.client.force_authenticate(user=self.user)
        image_bytes = BytesIO()
        Image.new("RGB", (2, 2), "red").save(image_bytes, format="PNG")
        uploaded_image = SimpleUploadedFile(
            "test.png", image_bytes.getvalue(), content_type="image/png"
        )

        with TemporaryDirectory() as media_dir, override_settings(MEDIA_ROOT=media_dir):
            post_data = {
                "author": self.user.pk,
                "title": "새 사진",
                "text": "새 설명",
                "image": uploaded_image,
            }
            response = self.client.post(
                self.post_url, post_data, format="multipart"
            )

        self.assertEqual(response.status_code, 201)
        self.assertEqual(Post.objects.count(), 2)
        created_post = Post.objects.get(title="새 사진")
        self.assertEqual(created_post.author, self.user)
        self.assertEqual(created_post.text, "새 설명")
        self.assertTrue(created_post.image.name.endswith("test.png"))
